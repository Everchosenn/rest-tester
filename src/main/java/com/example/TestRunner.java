package com.example;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// Клас для виконання HTTP-запитів
public class TestRunner {
    // Логгер для інформації та помилок
    private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);

    // Метод для виконання одного тесту
    public static TestResult runTest(TestCase testCase, String apiKey) {
        try {
            // Налаштування заголовків
            Map<String, String> headers = new HashMap<>();
            if (!testCase.headers().isEmpty()) {
                for (String header : testCase.headers().split(";")) {
                    String[] parts = header.split(":");
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }

            // Налаштування параметрів
            Map<String, String> params = new HashMap<>();
            if (!testCase.params().isEmpty()) {
                for (String param : testCase.params().split("&")) {
                    String[] parts = param.split("=");
                    params.put(parts[0].trim(), parts[1].trim());
                }
            }

            // Додаємо API Key до параметрів, якщо він введений
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                params.put("appid", apiKey); // Для OpenWeatherMap використовується параметр "appid"
                logger.info("Додано API Key до запиту: appid={}", maskApiKey(apiKey));
            }

            // Виконання запиту
            Response response;
            long startTime = System.currentTimeMillis();
            switch (testCase.method().toUpperCase()) {
                case "GET":
                    response = RestAssured.given()
                            .headers(headers)
                            .params(params)
                            .get(testCase.url());
                    break;
                case "POST":
                    response = RestAssured.given()
                            .headers(headers)
                            .params(params)
                            .body(testCase.body())
                            .post(testCase.url());
                    break;
                case "PUT":
                    response = RestAssured.given()
                            .headers(headers)
                            .params(params)
                            .body(testCase.body())
                            .put(testCase.url());
                    break;
                case "DELETE":
                    response = RestAssured.given()
                            .headers(headers)
                            .params(params)
                            .delete(testCase.url());
                    break;
                default:
                    return new TestResult("Невідомий метод!", 0, 0, 0, 0);
            }
            long timeTaken = System.currentTimeMillis() - startTime;
            long responseSize = response.getBody().asByteArray().length; // Розмір відповіді в байтах
            int statusCode = response.getStatusCode(); // Статус-код
            int headerCount = response.getHeaders().size(); // Кількість заголовків

            // Перевірка помилок автентифікації
            if (statusCode == 401) {
                String errorMessage = "Помилка автентифікації: статус 401 Unauthorized. Перевірте API Key.";
                logger.error(errorMessage);
                return new TestResult(errorMessage, timeTaken, responseSize, statusCode, headerCount);
            } else if (statusCode == 403) {
                String errorMessage = "Помилка автентифікації: статус 403 Forbidden. Доступ заборонено.";
                logger.error(errorMessage);
                return new TestResult(errorMessage, timeTaken, responseSize, statusCode, headerCount);
            }

            // Формування результату
            String result = String.format(
                    "Статус: %d\nЧас: %dмс\nТіло відповіді: %s",
                    statusCode, timeTaken, response.getBody().asString()
            );
            logger.info("Результат тесту: {}", result);

            // Перевірка статус-коду
            if (String.valueOf(statusCode).equals(testCase.expectedStatus())) {
                result += "\nТест пройшов успішно!";
            } else {
                result += "\nТест провалився!";
            }
            return new TestResult(result, timeTaken, responseSize, statusCode, headerCount);
        } catch (Exception e) {
            // Обробка помилок
            logger.error("Помилка при виконанні тесту: {}", e.getMessage());
            return new TestResult("Помилка: " + e.getMessage(), 0, 0, 0, 0);
        }
    }

    // Метод для маскування API Key у логах
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
