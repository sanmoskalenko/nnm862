(ns nnm862.handler-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [nnm862.server.handler :as sut]
    [unifier.response :as r]
    [nnm862.server.config :as config]
    [clojure.core.async :as async]))


(deftest exists-tag?-test
  (testing "Функция корректно обрабатывает тег,
            вызывая функцию по отправке запроса к API StackOverflow."
    (with-redefs [sut/send-request (constantly {:status 200
                                                :body   "{\"has_more\": true}"})]
      (let [expected true
            result   (#'sut/exists-tag? "clojure")]
        (is (= expected result)))))

  (testing "Функция корректно обрабатывает ошибки,
            при обращении к API StackOverflow."
    (with-redefs [sut/send-request (constantly {:status 404
                                                :body   "{\"has_more\": true}"})]
      (let [expected {"clojure" "Ошибка 404 при выполнении запроса к API StackOverflow endpoint `/tag` с тегом clojure"}
            result   (r/get-data (#'sut/exists-tag? "clojure"))]
        (is (= expected result)))))

  (testing "Функция корректно возвращает значение, если тег не найден,
            при обращении к API StackOverflow."
    (with-redefs [sut/send-request (constantly {:status 200
                                                :body   "{\"has_more\": false}"})]
      (let [expected false
            result   (#'sut/exists-tag? "clojure")]
        (is (= expected result))))))


(deftest search-for-tag-test
  (testing "Функция корректно обрабатывает тег,
            вызывая функцию по отправке запроса к API StackOverflow."
    (with-redefs [sut/send-request (constantly {:status 200
                                                :body   "{\"clojure\": \"some-value\"}"})]
      (let [expected {:clojure "some-value"}
            result   (#'sut/search-for-tag "clojure")]
        (is (= expected result)))))

  (testing "Функция корректно обрабатывает ошибки,
            при обращении к API StackOverflow."
    (with-redefs [sut/send-request (constantly {:status 404
                                                :body   "{\"clojure\": \"some-value\"}"})]
      (let [expected {"clojure" "Ошибка 404 при выполнении запроса к API StackOverflow endpoint `/search` с тегом clojure"}
            result   (r/get-data (#'sut/search-for-tag "clojure"))]
        (is (= expected result))))))


(deftest calculate-total-answ-test
  (testing "Расчет общего кол-ва ответов производится корректно."
    (let [sample {"clojure" {:answered nil}
                  "java-time" {:answered 1}
                  "lein" {:answered 2}}
          expected 3
          result (#'sut/calculate-total-answ sample)]
      (is (= expected result)))))


(deftest collect-stats-test
  (testing "При наличии ответа статистика по тегам подсчитывается корректно."
    (let [sample-acc {}
          sample-tags ["clojure" "java-time"]
          sample-has-answer true
          expected {"clojure" {:answered 1 :total 1}
                    "java-time" {:answered 1 :total 1}}
          result (#'sut/collect-stats sample-acc sample-tags sample-has-answer)]
      (is (= expected result))))

  (testing "При отсутствии ответа статистика по тегам подсчитывается корректно."
    (let [sample-acc {}
          sample-tags ["clojure" "java-time"]
          sample-has-answer false
          expected {"clojure" {:answered nil :total 1}
                    "java-time" {:answered nil :total 1}}
          result (#'sut/collect-stats sample-acc sample-tags sample-has-answer)]
      (is (= expected result)))))


(deftest aggregate-stats-test
  (testing "Агрегирующая статистика рассчитывается корректно."
    (let [sample-tag "clojure"
          sample-collect {"clojure" {:answered nil :total 1}
                          "java-time" {:answered nil :total 1}
                          "lein" {:answered 2 :total 2}}
          expected {"clojure" {:answered 2 :total 1}
                    "java-time" {:answered nil :total 1}
                    "lein" {:answered 2 :total 2}}
          result (#'sut/aggregate-stats sample-tag sample-collect)]
      (is (= expected result)))))


(deftest process-tag-test
  (testing "В случае получение ответа от API StackOverflow
            тег обрабатывается корректно."
    (with-redefs [sut/search-for-tag (constantly {:has_more true,
                                                  :items [{:is_answered true,
                                                           :tags ["ruby" "clojure"]}
                                                          {:is_answered false
                                                           :tags ["clojure"]}]})]
    (let [sample-tag "clojure"
          expected {"clojure" {:answered 1 :total 2}
                    "ruby" {:answered 1 :total 1}}
          result (#'sut/process-tag sample-tag)]
      (is (= expected result)))))

  (testing "В случае получение ответа отличного от 200 от API StackOverflow
            возвращается корректный резульат"
    (with-redefs [sut/send-request (constantly {:status 404})]
      (let [sample-tag "clojure"
            expected {"clojure" "Ошибка 404 при выполнении запроса к API StackOverflow endpoint `/search` с тегом clojure"}
            result (r/get-data (#'sut/process-tag sample-tag))]
        (is (= expected result)))))

  (testing "В случае, если включена проверка на наличие тега в базе
            StackOverflow и результат проверки отрицательный, то возвращается
            корректный результат."
    (with-redefs [config/ctx {:service {:tag-checker-on true}}
                  sut/send-request (constantly {:status 200
                                                :body   "{\"has_more\": false}"})]
      (let [expected "Запрос содержит несуществующий в API StackOverflow тег"
            result (#'sut/process-tag "clojure")]
        (is (= expected result))))))


(deftest process-all-tags-test
  (testing "Максимальное размер буфера не превышает пороговое значение,
            определенное в файле конфигурации."
    (with-redefs [config/ctx {:service {:buffer-size 2}}
                  async/<!! (fn [ch]
                              {:count (.n (.buf ch))})
                  sut/process-tag (constantly "clojure")]
    (let [sample-tags ["clojure" "scala" "python"]
          expected {:count 2}
          result (first (#'sut/process-all-tags sample-tags))]
      (is (= expected result)))))

  (testing "Создается необходимое кол-во буферов для обработки запроса."
    (with-redefs [config/ctx {:service {:buffer-size 10}}
                  async/<!! (fn [ch]
                              {:count (.n (.buf ch))})
                  sut/process-tag (constantly "clojure")]
    (let [sample-tags ["clojure" "scala" "python"]
          expected {:count 3}
          result (first (#'sut/process-all-tags sample-tags))]
      (is (= expected result)))))

  (testing "Запросы с завершившиеся ошибками возвращают корректный результат."
    (with-redefs [config/ctx {:service {:buffer-size 10}}
                  async/<!! (fn [_] (r/as-error "error"))
                  sut/process-tag (constantly "clojure")]
      (let [sample-tags ["clojure" "scala" "python"]
            expected '("error" "error" "error")
            result (#'sut/process-all-tags sample-tags)]
        (is (= expected result))))))


(deftest search-handler-test
  (testing "Запросы завершившиеся ошибками возвращают корректный результат
            (выброшенную ранее ошибку) в формате pretty JSON."
    (with-redefs [sut/process-tag (constantly "error")]
      (let [expected "{\n  \"headers\" : {\n    \"Content-Type\" : \"application/json\"\n  },\n  \"body\" : \"error\",\n  \"status\" : 200\n}"
            result (sut/search-handler {:params {:tag "clojure"}})]
        (is (= expected result)))))

  (testing "Если передан некорректный запрос, то возвращается корректная ошибка."
      (let [expected "{\n  \"headers\" : {\n    \"Content-Type\" : \"application/json\"\n  },\n  \"body\" : \"Параметры поиска заданы некорректно!\",\n  \"status\" : 200\n}"
            result (sut/search-handler {:params {:not-tag "incorrect-request"}})]
        (is (= expected result))))

  (testing "Если передано несколько тегов, то вызывается функция по асинхронной
            обработке тегов, результат возвращается в pretty JSON."
    (with-redefs [sut/process-all-tags (fn [_]
                                         '({"clojure" {:answered 1 :total 1}}
                                           {"scala" {:answered 2 :total 3}}))]
    (let [expected "{\n  \"headers\" : {\n    \"Content-Type\" : \"application/json\"\n  },\n  \"body\" : [ {\n    \"clojure\" : {\n      \"answered\" : 1,\n      \"total\" : 1\n    }\n  }, {\n    \"scala\" : {\n      \"answered\" : 2,\n      \"total\" : 3\n    }\n  } ],\n  \"status\" : 200\n}"
          result (sut/search-handler {:params {:tag ["clojure" "scala"]}})]
      (is (= expected result))))))
