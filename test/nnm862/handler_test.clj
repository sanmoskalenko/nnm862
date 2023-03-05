(ns nnm862.handler-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [nnm862.server.handler :as sut]
    [clj-http.client :as http]
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
    (with-redefs [http/get (fn [_ _] (throw (Exception.)))]
      (let [expected {"clojure" {:description "Error 500 when making a request to the StackOverflow API endpoint `/tags` with tag clojure"
                                 :message "Internal server error"
                                 :status 500}}
            result (r/get-data (#'sut/exists-tag? "clojure"))]
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
    (with-redefs [http/get (fn [_ _] (throw (Exception.)))]
      (let [expected  {"clojure" {:description "Error 500 when making a request to the StackOverflow API endpoint `/search` with tag clojure"
                                  :message "Internal server error"
                                  :status 500}}
            result (r/get-data (#'sut/search-for-tag "clojure"))]
        (is (= expected result))))))


(deftest aggregate-stats-test
  (testing "При наличии ответа статистика по тегам подсчитывается корректно."
    (let [sample-acc {}
          sample-tags ["clojure" "java-time"]
          sample-has-answer true
          expected {"clojure" {:answered 1 :total 1}
                    "java-time" {:answered 1 :total 1}}
          result (#'sut/aggregate-stats sample-acc sample-tags sample-has-answer)]
      (is (= expected result))))

  (testing "При отсутствии ответа статистика по тегам подсчитывается корректно."
    (let [sample-acc {}
          sample-tags ["clojure" "java-time"]
          sample-has-answer false
          expected {"clojure" {:answered nil :total 1}
                    "java-time" {:answered nil :total 1}}
          result (#'sut/aggregate-stats sample-acc sample-tags sample-has-answer)]
      (is (= expected result)))))


(deftest process-tag-test
  (testing "В случае получение ответа от API StackOverflow
            тег обрабатывается корректно."
    (with-redefs [sut/search-for-tag (constantly {:has_more true,
                                                  :items [{:is_answered true,
                                                           :tags ["ruby" "clojure"]}
                                                          {:is_answered false
                                                           :tags ["clojure"]}]})]
    (let [expected {"clojure" {:answered 1 :total 2}
                    "ruby" {:answered 1 :total 1}}
          result (#'sut/process-tag "clojure")]
      (is (= expected result)))))

  (testing "В случае получение ответа отличного от 200 от API StackOverflow
            возвращается корректный результат"
    (with-redefs [http/get (fn [_ _] (throw (Exception.)))]
      (let [expected {"clojure" {:description "Error 500 when making a request to the StackOverflow API endpoint `/search` with tag clojure"
                                 :message "Internal server error"
                                 :status 500}}
            result (r/get-data (#'sut/process-tag "clojure"))]
        (is (= expected result)))))

  (testing "В случае, если включена проверка на наличие тега в базе
            StackOverflow и результат проверки отрицательный, то возвращается
            корректный результат."
    (with-redefs [config/ctx {:service {:tag-checker-on true}}
                  sut/send-request (constantly {:status 200
                                                :body   "{\"has_more\": false}"})]
      (let [expected {"clojure" {:description "The request contains a tag that does not exist in the StackOverflow API"
                                 :message "Not found"
                                 :status 404}}
            result (r/get-data (#'sut/process-tag "clojure"))]
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

  (testing "Если передан некорректный запрос, то возвращается корректная ошибка с правильным статусом."
      (let [expected "{\n  \"headers\" : {\n    \"Content-Type\" : \"application/json\"\n  },\n  \"body\" : {\n    \"message\" : \"Bad request\",\n    \"description\" : \"Search parameters are set incorrectly!\"\n  },\n  \"status\" : 400\n}"
            result (sut/search-handler {:params {:not-tag "incorrect-request"}})]
        (is (= expected result))))

  (testing "Если передано несколько тегов, то вызывается функция по асинхронной
            обработке тегов, результат возвращается в pretty JSON."
    (with-redefs [sut/process-all-tags (fn [_]
                                         '({"clojure" {:answered 1 :total 1}}
                                           {"scala" {:answered 2 :total 3}}))]
    (let [expected "{\n  \"headers\" : {\n    \"Content-Type\" : \"application/json\"\n  },\n  \"body\" : [ {\n    \"clojure\" : {\n      \"answered\" : 1,\n      \"total\" : 1\n    }\n  }, {\n    \"scala\" : {\n      \"answered\" : 2,\n      \"total\" : 3\n    }\n  } ],\n  \"status\" : 200\n}"
          result (sut/search-handler {:params {:tag ["clojure" "scala"]}})]
      (is (= expected result)))))

  (testing "Если передан несуществующий тег, то в результирующий JSON он не попадает
            результат возвращается в pretty JSON."
    (with-redefs [http/get (fn [_ _] {:status 200
                                      :body "{\"items\":[{\"tags\":[\"ruby\",\"clojure\"],\"owner\":{\"account_id\":468,\"reputation\":13884,\"user_id\":609,\"user_type\":\"registered\",\"accept_rate\":65,\"profile_image\":\"https://i.stack.imgur.com/P0Jex.jpg?s=256&g=1\",\"display_name\":\"Justin Tanner\",\"link\":\"https://stackoverflow.com/users/609/justin-tanner\"},\"is_answered\":true,\"view_count\":86,\"accepted_answer_id\":75628436,\"answer_count\":4,\"score\":1,\"last_activity_date\":1677876969,\"creation_date\":1677852220,\"last_edit_date\":1677858419,\"question_id\":75628071,\"content_license\":\"CC BY-SA 4.0\",\"link\":\"https://stackoverflow.com/questions/75628071/in-clojure-how-to-partition-an-array-of-sorted-integers-into-contiguous-partitio\",\"title\":\"In clojure how to partition an array of sorted integers into contiguous partitions?\"}],\"has_more\":true,\"quota_max\":300,\"quota_remaining\":245}"})]
    (let [expected "{\n  \"headers\" : {\n    \"Content-Type\" : \"application/json\"\n  },\n  \"body\" : {\n    \"ruby\" : {\n      \"total\" : 1,\n      \"answered\" : 1\n    },\n    \"clojure\" : {\n      \"total\" : 1,\n      \"answered\" : 1\n    }\n  },\n  \"status\" : 200\n}"
          result (sut/search-handler {:params {:tag "кложа"}})]
      (is (= expected result))))))
