package com.example;

// Клас-модель для тест-кейсу
public record TestCase(
        String url, // URL запиту
        String method, // HTTP-метод
        String headers, // Заголовки
        String params, // Параметри
        String body, // Тіло запиту
        String expectedStatus // Очікуваний статус-код
) {
    // Методи геттерів генеруються автоматично
}