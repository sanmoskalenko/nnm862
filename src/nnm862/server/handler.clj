(ns nnm862.server.handler
  (:require
   [nnm862.server.config :refer [ctx]]
   [clojure.core.async :as async]
   [jsonista.core :as json]
   [clj-http.client :as http]
   [unifier.response :as r]))



(def ^{:doc "JSON декодер. Необходим для декодирования
             JSON сообщения в EDN формат."
       :private true}
  decode
  (json/object-mapper {:decode-key-fn true
                       :pretty true}))


(def ^{:doc "JSON энкодер. Необходим для кодирования
             EDN сообщение в JSON формат.
             Выводит результат в человеко-читаемом формате."
       :private true}
  encode
  (json/object-mapper {:encode-key-fn true
                       :pretty true}))


(defn- send-request
  "Отправляет GET запрос к переданному URL с заданными параметрами.

   Параметры:
    * `url` – адрес API;
    * `params` – парметры запроса."
  [url params]
 (try
   (http/get url {:query-params params})
   (catch Exception e
     (r/as-error (ex-message e)))))



(defn- exists-tag?
  "Проверяет наличие тега в библиотеке StackOverflow.
   Функция необходима для того, чтобы проверить наличие тега в базе
   StackOverflow, т.к. если тега не существует, то API StackOverflow
   при обращении к endpoint `/search` вернет результат с произвольными тегами.

   Отключена по-умолчанию. Для включения необходимо установить флаг `tag-checker-on`
   в `true` в файле конфигурации (config.edn).

   Параметры:
    * `tag` – искомый тег."
  [tag]
  (let [params {:pagesize 1
                :inname   tag
                :orders   "desc"
                :sort     "popular"
                :site     "stackoverflow"}
        url (-> ctx :service :search-tag-url)
        response (send-request url params)]
    (if (= 200 (:status response))
      (:has_more (json/read-value (:body response) decode))
      (r/as-error {tag
                   (format "Ошибка %s при выполнении запроса к API StackOverflow endpoint `/tag` с тегом %s"
                          (or (:status response) (r/get-data response)) tag)}))))


(defn- search-for-tag
  "Выполняет HTTP GET запрос к API StackOverflow для поиска
   вопросов по-заданному тэгу и возвращает результат в формате EDN.

   Параметры:
    * `tag` – наименование искомого тега.

   Субпараметры из контекста системы:
    * `page-limit` – количество запрашиваемых страниц к API StackOverflow;
    * `orders` – тип группировки результатов;
    * `sort` – тип сортировки ответов."
  [tag]
  (let [cfg (:service ctx)
        params {:pagesize (:page-limit cfg)
                :order    (:orders cfg)
                :sort     (:sort-by cfg)
                :tagged   tag
                :site     "stackoverflow"}
        url (-> ctx :service :search-url)
        response (send-request url params)]
    (if (= 200 (:status response))
      (json/read-value (:body response) decode)
      (r/as-error {tag
                   (format "Ошибка %s при выполнении запроса к API StackOverflow endpoint `/search` с тегом %s"
                          (or (:status response) (r/get-data response)) tag)}))))


(defn- calculate-total-answ
  "Рассчитывает общее кол-во ответов по всем тегами.

   Параметры:
    * `collected-stats` – `map` со статистикой по всем вопросам и тегам."
  [collected-stats]
  (reduce + (filter number?
                    (map (fn [[_subtag stats]]
                           (:answered stats))
                         collected-stats))))


(defn- collect-stats
  "Собирает статистику по тегам.

   Параметры:
    * `acc` – аккумулятор;
    * `tags` – субтеги по запросу;
    * `has-answer` – наличие ответа на вопрос (булевое значение)."
  [acc tags has-answer]
  (reduce (fn [inner-acc tag]
            (let [count (if (contains? inner-acc tag)
                          (-> tag inner-acc :total inc)
                          1)
                  answer-count (if has-answer
                                 (if (contains? inner-acc :answered-tags)
                                   (-> :answered-tags inner-acc inc)
                                   1)
                                 (inner-acc :answered-tags))]
              (assoc-in inner-acc [tag]
                        {:total count
                         :answered answer-count})))
          acc tags))


(defn- aggregate-stats
  "Агрегирует статистику, по искомому тегу, возвращая
  `map` с пересчитанной статистикой.

   Параметры:
    * `tag` – имя тега, переданного в запросе;
    * `collected-stats` – общая статистика по всем тегам."
  [tag collected-stats]
  (update-in collected-stats [tag :answered]
             (-> collected-stats
                 calculate-total-answ
                 constantly)))


(defn- process-tag
  "Обрабатывает результаты поиска для каждого
   тега и вычисляет суммарную статистику по всем тегам.

   Параметры:
    * `tag` – имя тега, переданного в запросе."
  [tag]
  (let [cfg (-> ctx :service)
        tag-exists? (if (:tag-checker-on cfg)
                          (exists-tag? tag)
                          true)
        data (if tag-exists?
               (search-for-tag tag)
               (r/as-error "Запрос содержит несуществующий в API StackOverflow тег"))
        tag-stats (if (r/error? data)
                    (r/get-data data)
                    (reduce (fn [acc question]
                              (let [tags (:tags question)
                                    has-answer (:is_answered question)
                                    collected-stats (collect-stats acc tags has-answer)]
                                (aggregate-stats tag collected-stats)))
                            {} (:items data)))]
    tag-stats))


(defn- process-all-tags
  "Обрабатывает все переданные теги, выполняя поиск вопросов и вычисляя
   общую статистику.

   Обратите внимание: если проверка на существование тега отключена,
   то запрос на поиск ответов будет выполняться всегда, не зависимо от того
   существует ли тег в действительности или нет. Если проверка включена, то
   Поиск выполняться не будет. По-умолчанию отключено!

   Параметры:
    * `tags` – вектор тегов, переданных в запросе."
  [tags]
  (let [cfg                 (-> ctx :service)
        buffer-size         (:buffer-size cfg)
        count-tags          (count tags)
        count-buffers       (min buffer-size count-tags)
        ch                  (async/chan count-buffers)]
    (doseq [tag tags]
      (async/go
        (async/>! ch (process-tag tag))))
    (repeatedly count-buffers #(when-let [result (async/<!! ch)]
                                 (if (r/error? result)
                                   (r/get-data result)
                                   (into {} result))))))


(defn search-handler
  "Обрабатывает HTTP запросы к URL `/search`.
   Результаты обработки возвращаются в формате pretty JSON.

   Параметры:
    * `req` – необработанный запрос."
  [req]
  (let [tags      (-> req :params :tag)
        tag-stats (cond-> tags
                          (vector? tags) process-all-tags
                          (string? tags) process-tag
                          (nil? tags) (str "Параметры поиска заданы некорректно!"))]
    (json/write-value-as-string
      {:headers {:Content-Type "application/json"}
       :body    tag-stats
       :status  200}
      encode)))

