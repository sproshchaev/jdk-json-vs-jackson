# jdk-json-vs-jackson

[![check](https://github.com/sproshchaev/jdk-json-vs-jackson/actions/workflows/check.yml/badge.svg)](https://github.com/sproshchaev/jdk-json-vs-jackson/actions/workflows/check.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Сергей Прощаев**

[![Website](https://img.shields.io/badge/prosoft.pages.dev-blue?logo=googlechrome&logoColor=white)](https://prosoft.pages.dev)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-sproshchaev-0A66C2)](https://www.linkedin.com/in/sproshchaev/)
[![Habr](https://img.shields.io/badge/Habr-sproshchaev-65A3BE?logo=habr&logoColor=white)](https://habr.com/ru/users/sproshchaev/)

---

## 1. Что это

Учебный пример к статье на Хабре
"[JSON в JDK 28 без Jackson: где хватит встроенного парсера, а где нет](https://habr.com/ru/companies/otus/articles/1101904/)".

В JDK 28 появился инкубаторный модуль `jdk.incubator.json`
([JEP 540](https://openjdk.org/jeps/540)): разбор и генерация JSON без внешних библиотек.
Здесь одни и те же задачи решены трижды: встроенным API, деревом Jackson 3 и привязкой
Jackson к записям. Все три варианта сравниваются по поведению на граничных случаях,
по скорости, памяти и цене старта.

## 2. Что внутри

```text
data/                       тестовые документы, генерируются DataGen с seed 42,
                            в дампе потоков waitingOn только у WAITING и TIMED_WAITING,
                            blockedOn у BLOCKED, как в JSON-дампе jcmd JDK 28
                            (поле ставится половине таких потоков, ни одна задача его не читает)
expected.txt                эталон поведения для run.sh
run.sh                      сборка, сверка, замеры
src/main/java/demo/
  DataFiles.java            пути к тестовым документам
  JsonTasks.java            шесть задач
  JdkTasks.java             решение на jdk.incubator.json
  JacksonTreeTasks.java     решение на дереве Jackson
  JacksonBindingTasks.java  решение на записях Jackson
  Compare.java              ответы трех реализаций рядом
  EdgeCases.java            граничные случаи и генерация
  Footprint.java            jar-файлы, классы, холодный запуск
  OneShotJdk.java           программа для замера запуска, JDK
  OneShotJackson.java       программа для замера запуска, Jackson
  Retained.java             память, удерживаемая деревом
  Walk.java                 обход дерева с чтением всех значений
  Report.java               таблица из target/jmh.json
  DataGen.java              генератор документов
  bench/                    JMH: разбор и задачи целиком
```

## 3. Требования

- JDK 28, ранняя сборка с [jdk.java.net/28](https://jdk.java.net/28/). Проверено на 28-ea+23.
- Maven 3.9, запущенный на этой JDK (`mvn -v` показывает `Java version: 28`).
- Jackson 3.3.0 и JMH 1.37 Maven скачает сам.

## 4. Быстрый старт

```bash
git clone https://github.com/sproshchaev/jdk-json-vs-jackson.git
cd jdk-json-vs-jackson
export JAVA_HOME=/path/to/jdk-28 PATH="$JAVA_HOME/bin:$PATH"
```

```bash
./run.sh quick   # данные и поведение, меньше минуты
./run.sh ci      # то же плюс короткий прогон замеров, как в CI
./run.sh         # плюс старт, память и JMH, около 6 минут
```

`run.sh` собирает проект, заново генерирует тестовые документы и сверяет их с `data/`
байт в байт, затем прогоняет задачи и граничные случаи и сравнивает вывод с `expected.txt`.
При расхождении код выхода 1. Времена и память зависят от машины, поэтому печатаются,
но с эталоном не сверяются.

## 5. Результаты

### Поведение по умолчанию

Полная таблица лежит в `expected.txt`, она же эталон для `run.sh`. Главное:

| Вход | JDK 28 | Jackson 3, дерево |
|---|---|---|
| `{"a":1,"a":2}` | ошибка разбора | `2`, молча |
| `1.5` прочитать как `int` | ошибка | `1` |
| `"42"` прочитать как `int` | ошибка | `42` |
| `1e400` прочитать как `double` | ошибка | `Infinity` |
| отсутствующее поле как `int` (`path()` у Jackson дает `MissingNode`) | ошибка | `0` |
| `null` прочитать как строку | ошибка | пустая строка |
| `0.1000000000000000055511151231257827` записать обратно | исходный текст | `0.1` |
| вложенность 2000 и 100000 | разбирает | отказ, предел 500 |
| число из 2000 цифр | разбирает, `asDouble` дает ошибку | отказ, предел длины числа |

Комментарии, запятую в конце, одинарные кавычки, `NaN`, ведущий ноль, BOM и второй документ
после первого оба парсера по умолчанию отвергают.

В колонке Jackson стоят методы `asInt()`, `asString()`, `asDouble()`. У дерева Jackson 3
есть их пары `intValue()`, `stringValue()`, `doubleValue()`, и ведут они себя иначе
(раздел `as*() vs *Value()` в `expected.txt`):

| Вход | `as*()` | `*Value()` |
|---|---|---|
| `1.5`, `"42"`, отсутствующее поле как `int` | `1`, `42`, `0` | ошибка |
| `null` как строка | `""` | `null` |
| `1e400` как `double` | `Infinity` | `Infinity` |

Две настройки переключают дерево Jackson на строгое поведение (раздел `default vs option`):
`StreamReadFeature.STRICT_DUPLICATE_DETECTION` отвергает дубль ключа,
`JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS` сохраняет дробь до знака.

### Скорость и память

Apple M2 Pro, macOS 26.6, JDK 28-ea+23, Jackson 3.3.0, JMH 1.37, один форк,
3 по 2 с прогрева и 5 по 2 с замера. Документ уже лежит в памяти строкой.

Дерево JDK ленивое: каждый узел хранит ссылку на весь документ как `char[]` и смещения,
а строки и числа вычисляются при первом обращении (`LazyConstant` во внутренних классах
`jdk.incubator.json.impl`). Поэтому замеров два: только разбор и разбор с чтением
всех значений (`demo.Walk`).

| Документ | Замер | JDK, мкс | Jackson, мкс | JDK, выделено КБ | Jackson, выделено КБ |
|---|---|---:|---:|---:|---:|
| `forecast.json`, 4,5 КБ | только разбор | 8,7 ± 0,1 | 8,6 ± 0,2 | 36,8 | 12,7 |
| | разбор и чтение всего | 28,8 ± 11,4 | 10,7 ± 1,0 | 54,2 | 16,5 |
| `threaddump-medium.json`, 123 КБ | только разбор | 152 ± 10 | 144 ± 3 | 561 | 238 |
| | разбор и чтение всего | 328 ± 148 | 184 ± 22 | 956 | 310 |
| `threaddump-large.json`, 12 МБ | только разбор | 16 065 ± 2 428 | 14 225 ± 904 | 55 178 | 23 327 |
| | разбор и чтение всего | 29 061 ± 2 562 | 17 477 ± 845 | 94 163 | 30 721 |

Погрешность - доверительный интервал JMH 99,9%. Пока значения не читаются, скорость
на равных. Когда читается все, JDK выделяет в 3 раза больше памяти на операцию и медленнее:
на 12 МБ в 1,65 раза при узкой погрешности, на малых документах разброс между прогонами велик.

Память, которую удерживает дерево 12 МБ текста после полной сборки мусора (`demo.Retained`):

| | Сразу после разбора | После чтения всех значений |
|---|---:|---:|
| JDK | 52 МБ | 76 МБ |
| Jackson, дерево | 23 МБ | 24 МБ |

Целая задача "разобрать, пройти по дереву, посчитать", мкс:

| Задача | JDK | Jackson, дерево | Jackson, записи |
|---|---:|---:|---:|
| средняя температура, 4,5 КБ | 10,5 | 8,9 | 9,3 |
| потоки по состояниям, 123 КБ | 166 | 157 | 145 |

В задачах читается малая часть значений, поэтому разница небольшая.

### Холодный запуск

Программа целиком: новая JVM читает `forecast.json`, разбирает его, печатает одно число
и завершается. Медиана 15 запусков после прогревочного прохода, варианты чередуются. Флаг `--add-modules jdk.incubator.json`
получает только вариант JDK, точки входа раздельные (`OneShotJdk`, `OneShotJackson`):

| Вариант | Загружено классов | Время, мс |
|---|---:|---:|
| JDK | 1153 | 70 |
| Jackson, дерево | 1834 | 170 |
| Jackson, записи | 2163 | 201 |

Jackson добавляет три jar-файла общим размером 2,6 МБ.

## 6. Ограничения

- `jdk.incubator.json` - инкубаторный модуль: API может измениться до финальной версии,
  запуск требует `--add-modules jdk.incubator.json`.
- Jackson сравнивается с настройками по умолчанию. Строгость дублей, приведение типов и
  точность чисел в нем настраиваются (`STRICT_DUPLICATE_DETECTION`,
  `USE_BIG_DECIMAL_FOR_FLOATS` и другие), пределы задаются через `StreamReadConstraints`.
- Встроенный API не умеет привязку к объектам, потоковое чтение и чтение из `Reader` или
  `InputStream`: документ целиком загружается в `String` или `char[]`. Это прямо записано
  в целях JEP 540.
- Замеры сделаны на одной машине и ранней сборке JDK, цифры в CI и у вас будут другими.

## 7. Лицензия

MIT, см. [LICENSE](LICENSE).

## 8. Полезные ссылки

- [Статья на Хабре](https://habr.com/ru/companies/otus/articles/1101904/)
- [JEP 540: Simple JSON API (Incubator)](https://openjdk.org/jeps/540)
- [OpenJDK JDK 28 Early-Access Builds](https://jdk.java.net/28/)
- [Jackson 3](https://github.com/FasterXML/jackson)
- [RFC 8259: The JavaScript Object Notation (JSON) Data Interchange Format](https://datatracker.ietf.org/doc/html/rfc8259)
