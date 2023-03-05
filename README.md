# nnm862

Демонстрационное приложение на `Clojure` выполняет запрос к API `StackOverflow` для подсчета количества ответов по тегам. Поддерживает асинхронную работу.

Для ассинхронной работы используется библиотека `clojure.core.async`: https://github.com/clojure/core.async.

Приложение конфигурируется через файл, расположенный по пути`resources/config.edn`. Поддержана конфигурация через переменные окружения.

## Использование

> :warning: Перед использованием убедитесь, что у вас установлены следующие компоненты:
> 1. `leiningen` – для установки перейдите на сайт https://leiningen.org/.
> 2. `java` – для установки перейдите на сайт https://www.java.com/.

* Для запуска выполните следующую команду:

```bash
    lein run
```

* Для сборки приложения в `uberjar` выполните команду: 

```bash
    lein uberjar
```

* Для запуска собранного приложения выполните команду:

```bash
    java -jar nnm862.jar
```

* Для тестирования приложения выполните команду: 

```bash
    lein test
```

* Для обращения к API запущенного приложения можно использовать следующую команду:

```bash
    curl -X GET "http://localhost:8080/search?tag=clojure&tag=scala"
```

## Опции

Допустимые опции для конфигурации приложения:

#### HTTP порт:
```clojure
    ;; config.edn
    web/port
    ;; переменная окружения
    WEBSERVER_PORT
```
Значение по-умолчанию: `8080`.

#### Максимальный размер буфера для обработки запросов в асинхронном режиме

```clojure
    ;; config.edn
    service/buffer-size
    ;; переменная окружения
    BUFFER_SIZE
```

Значение по-умолчанию: `10`.

#### Адрес сервиса StackOverflow для поиска вопросов по тегам (/search)

```clojure
    ;; config.edn
    service/search-url
    ;; переменная окружения
    SEARCH_URL
```
Поддерживаются следующие версии API:
* 2.2;
* 2.3.

Значение по-умолчанию: `https://api.stackexchange.com/2.3/search`.

#### Необходимость проверки на наличие тега в StackOverflow

```clojure
    ;; config.edn
    service/tag-checker-on
    ;; переменная окружения
    TAG_CHECKER_ON
```
Значение по-умолчанию: `false`.

#### Адрес сервиса StackOverflow для поиска тегов (/tags)

```clojure
    ;; config.edn
    service/search-tag-url
    ;; переменная окружения
    SEARCH_TAG_URL
```
Поддерживаются следующие версии API:
* 2.2;
* 2.3.

> :warning: Обратите внимание! По-умолчанию ***поиск тегов отключен в приложении***!

Значение по-умолчанию: `https://api.stackexchange.com/2.3/tags`.

#### Стиль сортировки результатов

```clojure
    ;; config.edn
    service/orders
    ;; переменная окружения
    ORDERS
```
Допустимые значения: 
* `asc` – восходящий стиль сортировки. Используется для сортировки результатов запроса в стиле "сверху вниз";
* `desc` – нисходящий стиль сортировки. Используется для сортировки результатов запроса в стиле "снизу вверх".

Значение по-умолчанию: `desc`.

#### Сортировка результатов

```clojure
    ;; config.edn
    service/sort-by
    ;; переменная окружения
    SORT_BY
```
Допустимые значение: 
* `activity` – сортировка по последней активности в вопросах;
* `votes` – сортировка по количеству голосов в вопросах;
* `creation` – сортировка по дате создания;
* `relevance` – сортировка по релевантности результатов.

Значение по-умолчанию: `creation`.

#### Ограничение на количество запрашиваемых страниц StackOverflow
```clojure
    ;; config.edn
    service/page-limit
    ;; переменная окружения
    PAGE_LIMIT
```

Значение по-умолчанию: `100`.

## Пример возвращаемого результата

* Если запрос сформирован корректно: 
```json
{
  "headers" : {
    "Content-Type" : "application/json"
  },
  "body" : {
    "json" : {
      "total" : 1,
      "answered" : 1
    },
    "boolean" : {
      "total" : 1,
      "answered" : 1
    },
    "transducer" : {
      "total" : 1,
      "answered" : 1
    },
    "clojure" : {
      "total" : 10,
      "answered" : 6
    }
  },
  "status" : 200
}%
```

* Если запрос сформирован с ошибкой: 
```json
{
  "headers" : {
    "Content-Type" : "application/json"
  },
  "body" : {
    "message" : "Bad request",
    "description" : "Search parameters are set incorrectly!"
  },
  "status" : 400
}%
```


Copyright © 2023 sanmoskalenko

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
