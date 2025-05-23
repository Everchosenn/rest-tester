package com.example;

// Клас для збереження результатів тесту
public record TestResult(
        String result, // Текстовий результат тесту
        long timeTaken, // Час виконання (мс)
        long responseSize, // Розмір відповіді (байти)
        int statusCode, // Статус-код відповіді
        int headerCount // Кількість заголовків у відповіді
) {
    // Методи геттерів генеруються автоматично
}