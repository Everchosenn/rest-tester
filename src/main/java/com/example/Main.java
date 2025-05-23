package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.*;

// Основний клас програми, який запускає JavaFX додаток для тестування REST API
public class Main extends Application {
    // Поля для GUI елементів
    private TextField urlField; // Поле для введення URL запиту
    private ComboBox<String> methodCombo; // Випадаючий список для вибору HTTP-методу
    private TextField headersField; // Поле для введення заголовків запиту
    private TextField paramsField; // Поле для введення параметрів запиту
    private TextField apiKeyField; // Поле для введення API ключа
    private TextArea bodyField; // Текстове поле для введення тіла запиту
    private TextArea resultArea; // Текстове поле для відображення результатів тестування
    private Spinner<Integer> runCountSpinner; // Лічильник для кількості запусків тесту
    private Spinner<Integer> periodicIntervalSpinner; // Лічильник для інтервалу періодичних тестів
    private Spinner<Integer> periodicDurationSpinner; // Лічильник для тривалості періодичних тестів
    private TextField scheduleStartTimeField; // Поле для введення часу запланованого тесту
    private final List<TestCase> testCases = new ArrayList<>(); // Список збережених тест-кейсів
    private final List<RequestHistory> requestHistory = new ArrayList<>(); // Історія виконаних запитів
    private ComboBox<RequestHistory> requestSelector; // Випадаючий список для вибору історії запитів
    private LineChart<Number, Number> timeChart; // Графік для відображення часу виконання запитів
    private LineChart<Number, Number> sizeChart; // Графік для відображення розміру відповідей
    private int periodicRunCount = 0; // Лічильник кількості періодичних запусків
    private Timer periodicTimer; // Таймер для періодичних тестів
    private Timer scheduleTimer; // Таймер для запланованих тестів
    private static final String LOG_FILE_PATH = "rest-tester.log"; // Шлях до файлу логів
    private static final String DEFAULT_API_KEY_PATH = "api-key.txt"; // Шлях до файлу з API ключем за замовчуванням
    private String reportDirectory = "reports"; // Папка для зберігання звітів
    private String testsDirectory = "tests"; // Папка для зберігання тест-кейсів
    private static final int MAX_LOG_LINES = 2000; // Максимальна кількість рядків у файлі логів
    // Нові елементи для метрик
    private ComboBox<String> timeMetricsCombo; // Випадаючий список для вибору метрик часу виконання
    private Label timeMetricLabel; // Мітка для відображення обраної метрики часу
    private ComboBox<String> sizeMetricsCombo; // Випадаючий список для вибору метрик розміру відповіді
    private Label sizeMetricLabel; // Мітка для відображення обраної метрики розміру

    @Override
    public void start(Stage primaryStage) {
        // Перевірка та створення папки для тестів
        File testsFolder = new File(testsDirectory);
        if (!testsFolder.exists()) {
            if (!testsFolder.mkdirs()) {
                resultArea.setText("Помилка: не вдалося створити папку для тестів: " + testsDirectory + "\n");
                return;
            }
        } else if (!testsFolder.isDirectory()) {
            resultArea.setText("Помилка: шлях для тестів не є папкою: " + testsDirectory + "\n");
            return;
        } else if (!testsFolder.canRead() || !testsFolder.canWrite()) {
            resultArea.setText("Помилка: немає прав на читання або запис у папку для тестів: " + testsDirectory + "\n");
            return;
        }

        // Завантаження збережених тест-кейсів із папки
        testCases.addAll(TestGenerator.loadTestCases(testsDirectory));

        // Перевірка та створення папки для звітів
        File reportsFolder = new File(reportDirectory);
        if (!reportsFolder.exists()) {
            if (!reportsFolder.mkdirs()) {
                resultArea.setText("Помилка: не вдалося створити папку для звітів: " + reportDirectory + "\n");
                return;
            }
        } else if (!reportsFolder.isDirectory()) {
            resultArea.setText("Помилка: шлях для звітів не є папкою: " + reportDirectory + "\n");
            return;
        } else if (!reportsFolder.canRead() || !reportsFolder.canWrite()) {
            resultArea.setText("Помилка: немає прав на читання або запис у папку для звітів: " + reportDirectory + "\n");
            return;
        }

        // Завантаження API ключа із файлу за замовчуванням
        loadApiKeyFromDefaultFile();

        // Створення вкладок для інтерфейсу
        TabPane tabPane = new TabPane();

        // Вкладка для тестування
        Tab testTab = new Tab("Тестування");
        testTab.setClosable(false);
        VBox testPane = createTestPane();
        testTab.setContent(testPane);

        // Вкладка для графіків
        Tab chartTab = new Tab("Графіки");
        chartTab.setClosable(false);
        VBox chartPane = createChartPane();
        chartTab.setContent(chartPane);

        // Вкладка з довідкою
        Tab helpTab = new Tab("Довідка");
        helpTab.setClosable(false);
        VBox helpPane = createHelpPane();
        helpTab.setContent(helpPane);

        tabPane.getTabs().addAll(testTab, chartTab, helpTab);

        // Налаштування сцени та відображення вікна
        Scene scene = new Scene(tabPane, 600, 700);
        primaryStage.setTitle("REST Tester");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Перевірка файлу логів і очищення, якщо кількість рядків перевищує максимум
    private void checkAndClearLogFile() {
        File logFile = new File(LOG_FILE_PATH);
        if (!logFile.exists()) {
            return;
        }

        if (!logFile.canRead()) {
            System.err.println("Помилка: немає прав на читання файлу логів: " + LOG_FILE_PATH);
            return;
        }

        if (!logFile.canWrite()) {
            System.err.println("Помилка: немає прав на запис у файл логів: " + LOG_FILE_PATH);
            return;
        }

        try {
            // Підрахунок кількості рядків у файлі логів
            long lineCount = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                while (reader.readLine() != null) {
                    lineCount++;
                }
            }

            // Якщо кількість рядків перевищує максимум, очищаємо файл
            if (lineCount >= MAX_LOG_LINES) {
                try (FileWriter writer = new FileWriter(logFile, false)) {
                    writer.write("");
                    System.out.println("Файл логів очищено, оскільки кількість рядків досягла " + MAX_LOG_LINES);
                }
            }
        } catch (IOException e) {
            System.err.println("Помилка при перевірці або очищенні файлу логів: " + e.getMessage());
        }
    }

    // Створення панелі з довідковою інформацією
    private VBox createHelpPane() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Створення акордеону для відображення різних розділів довідки
        Accordion accordion = new Accordion();
        accordion.setExpandedPane(null);
        accordion.getPanes().addListener((javafx.collections.ListChangeListener<TitledPane>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (TitledPane pane : change.getAddedSubList()) {
                        pane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {});
                    }
                }
            }
        });

        // Розділ "Про програму"
        TextArea aboutProgramText = new TextArea();
        aboutProgramText.setEditable(false);
        aboutProgramText.setWrapText(true);
        aboutProgramText.setText(
                "Про програму\n\n" +
                        "Назва: REST Tester\n" +
                        "Версія: 1.0\n" +
                        "Призначення: Програма призначена для тестування REST API запитів. Вона дозволяє створювати, зберігати та запускати тести, а також аналізувати результати через графіки та звіти.\n"
        );
        TitledPane aboutProgramPane = new TitledPane("Про програму", aboutProgramText);
        aboutProgramPane.setExpanded(true);

        // Розділ "Що таке REST API"
        TextArea restApiText = new TextArea();
        restApiText.setEditable(false);
        restApiText.setWrapText(true);
        restApiText.setText(
                "Що таке REST API\n\n" +
                        "REST (Representational State Transfer) — це архітектурний стиль для створення веб-сервісів. REST API дозволяє клієнтам взаємодіяти з сервером через HTTP-запити.\n\n" +
                        "Основні методи HTTP:\n" +
                        "- GET: Отримання даних (наприклад, список користувачів).\n" +
                        "- POST: Створення нових даних (наприклад, додавання користувача).\n" +
                        "- PUT: Оновлення існуючих даних.\n" +
                        "- DELETE: Видалення даних.\n\n"
        );
        TitledPane restApiPane = new TitledPane("Що таке REST API", restApiText);

        // Розділ "Статус-коди HTTP"
        TextArea statusCodesText = new TextArea();
        statusCodesText.setEditable(false);
        statusCodesText.setWrapText(true);
        statusCodesText.setText(
                "Статус-коди HTTP\n\n" +
                        "Статус-коди HTTP вказують на результат виконання запиту. Ось найпоширеніші з них:\n\n" +
                        "- 200 OK: Запит виконано успішно.\n" +
                        "- 201 Created: Ресурс успішно створено (зазвичай після POST).\n" +
                        "- 400 Bad Request: Некоректний запит (наприклад, неправильні параметри).\n" +
                        "- 401 Unauthorized: Помилка автентифікації (неправильний API Key).\n" +
                        "- 403 Forbidden: Доступ заборонено.\n" +
                        "- 404 Not Found: Ресурс не знайдено.\n" +
                        "- 405 Method Not Allowed: Сервер не приймає ваш запит, тому що ви використовуєте HTTP-метод, що не підтримується.\n" +
                        "- 500 Internal Server Error: Помилка на стороні сервера."
        );
        TitledPane statusCodesPane = new TitledPane("Статус-коди HTTP", statusCodesText);

        // Розділ "Інструкція з використання"
        TextArea instructionsText = new TextArea();
        instructionsText.setEditable(false);
        instructionsText.setWrapText(true);
        instructionsText.setText(
                "Інструкція з використання\n\n" +
                        "1. Як запустити тест:\n" +
                        "   - Введіть URL у поле 'URL' (наприклад, https://api.example.com).\n" +
                        "   - Виберіть метод (GET, POST тощо).\n" +
                        "   - За потреби введіть заголовки, параметри, тіло запиту та API Key.\n" +
                        "   - Вкажіть кількість запусків у лічильнику.\n" +
                        "   - Натисніть 'Виконати тест'.\n\n" +
                        "2. Як зберегти тест:\n" +
                        "   - Введіть дані для тесту.\n" +
                        "   - Натисніть 'Зберегти тест'. Тест збережеться у папці, яку ви обрали (за замовчуванням 'tests').\n\n" +
                        "3. Як запустити всі тести:\n" +
                        "   - Натисніть 'Запустити всі тести'. Програма виконає всі збережені тести.\n\n" +
                        "4. Як згенерувати звіт:\n" +
                        "   - Перейдіть на вкладку 'Графіки'.\n" +
                        "   - Виберіть запит зі списку.\n" +
                        "   - Натисніть 'Згенерувати звіт'. Звіт збережеться у папці 'reports'.\n\n" +
                        "5. Як вибрати папку для тестів:\n" +
                        "   - Натисніть 'Вибрати папку для тестів' і оберіть потрібну папку."
        );
        TitledPane instructionsPane = new TitledPane("Інструкція з використання", instructionsText);

        accordion.getPanes().addAll(aboutProgramPane, restApiPane, statusCodesPane, instructionsPane);

        // Додавання прокрутки для акордеону
        ScrollPane scrollPane = new ScrollPane(accordion);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().add(scrollPane);
        return root;
    }

    // Завантаження API ключа із файлу за замовчуванням
    private void loadApiKeyFromDefaultFile() {
        File file = new File(DEFAULT_API_KEY_PATH);
        if (file.exists()) {
            if (!file.canRead()) {
                resultArea.setText("Помилка: немає прав на читання файлу API Key: " + DEFAULT_API_KEY_PATH + "\n");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String apiKey = reader.readLine();
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    apiKeyField.setText(apiKey.trim());
                }
            } catch (IOException e) {
                resultArea.setText("Помилка при завантаженні API Key: " + e.getMessage() + "\n");
            }
        }
    }

    // Зупинка таймерів при закритті програми
    @Override
    public void stop() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
        }
        if (scheduleTimer != null) {
            scheduleTimer.cancel();
        }
    }

    // Створення панелі для вкладки "Тестування"
    private VBox createTestPane() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Поле введення URL
        urlField = new TextField();
        urlField.setPromptText("Введіть URL (наприклад, https://api.example.com)");

        // Вибір HTTP-методу
        methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll("GET", "POST", "PUT", "DELETE");
        methodCombo.setValue("GET");

        // Поле введення API ключа
        apiKeyField = new TextField();
        apiKeyField.setPromptText("API Key (опціонально)");

        // Кнопка для збереження API ключа
        Button saveApiKeyButton = new Button("Зберегти ключ як");
        saveApiKeyButton.setOnAction(e -> saveApiKey());

        // Кнопка для завантаження API ключа
        Button loadApiKeyButton = new Button("Завантажити ключ");
        loadApiKeyButton.setOnAction(e -> loadApiKey());

        // Рядок для API ключа та кнопок
        HBox apiKeyRow = new HBox(10);
        apiKeyRow.getChildren().addAll(new Label("API Key:"), apiKeyField, saveApiKeyButton, loadApiKeyButton);

        // Поле введення заголовків
        headersField = new TextField();
        headersField.setPrefWidth(500);
        headersField.setPromptText("Заголовки (наприклад, Content-Type:application/json)");

        // Поле введення параметрів
        paramsField = new TextField();
        paramsField.setPrefWidth(500);
        paramsField.setPromptText("Параметри (наприклад, id=1&name=test)");

        // Поле введення тіла запиту
        bodyField = new TextArea();
        bodyField.setPrefWidth(500);
        bodyField.setPrefHeight(150);
        bodyField.setPromptText("Тіло запиту (для POST/PUT)");
        VBox.setVgrow(bodyField, Priority.ALWAYS);

        // Лічильник кількості запусків
        runCountSpinner = new Spinner<>(1, 100, 1);
        runCountSpinner.setEditable(true);
        runCountSpinner.setPrefWidth(100);
        runCountSpinner.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = runCountSpinner.getEditor().getText();
                if (text == null || text.trim().isEmpty()) {
                    runCountSpinner.getEditor().setText("1");
                    runCountSpinner.getValueFactory().setValue(1);
                }
            }
        });

        // Лічильник інтервалу для періодичних тестів
        periodicIntervalSpinner = new Spinner<>(1, 60, 5);
        periodicIntervalSpinner.setEditable(true);
        periodicIntervalSpinner.setPrefWidth(100);
        periodicIntervalSpinner.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = periodicIntervalSpinner.getEditor().getText();
                if (text == null || text.trim().isEmpty()) {
                    periodicIntervalSpinner.getEditor().setText("1");
                    periodicIntervalSpinner.getValueFactory().setValue(1);
                }
            }
        });

        // Лічильник тривалості періодичних тестів
        periodicDurationSpinner = new Spinner<>(1, 1440, 60);
        periodicDurationSpinner.setEditable(true);
        periodicDurationSpinner.setPrefWidth(100);
        periodicDurationSpinner.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = periodicDurationSpinner.getEditor().getText();
                if (text == null || text.trim().isEmpty()) {
                    periodicDurationSpinner.getEditor().setText("1");
                    periodicDurationSpinner.getValueFactory().setValue(1);
                }
            }
        });

        // Поле для введення часу запланованого тесту
        scheduleStartTimeField = new TextField();
        scheduleStartTimeField.setPromptText("Час початку (HH:mm)");
        scheduleStartTimeField.setPrefWidth(150);

        // Кнопка для запуску одного тесту
        Button runButton = new Button("Виконати тест");
        runButton.setOnAction(e -> runSingleTest());

        // Кнопка для збереження тесту
        Button saveButton = new Button("Зберегти тест");
        saveButton.setOnAction(e -> saveTestCase());

        // Кнопка для запуску всіх тестів
        Button runAllButton = new Button("Запустити всі тести");
        runAllButton.setOnAction(e -> runAllTests());

        // Кнопка для вибору та запуску тесту
        Button selectAndRunButton = new Button("Вибрати та запустити тест");
        selectAndRunButton.setOnAction(e -> runSelectedTest());

        // Кнопка для очищення всіх полів
        Button clearButton = new Button("Очистити поля");
        clearButton.setOnAction(e -> clearFields());

        // Кнопка для запуску періодичних тестів
        Button periodicRunButton = new Button("Запустити періодично");
        periodicRunButton.setOnAction(e -> runPeriodicTest());

        // Кнопка для зупинки періодичних тестів
        Button stopPeriodicButton = new Button("Зупинити періодичні запити");
        stopPeriodicButton.setOnAction(e -> stopPeriodicTest());

        // Кнопка для запланованого виконання
        Button scheduleRunButton = new Button("Запланувати виконання");
        scheduleRunButton.setOnAction(e -> scheduleTest());

        // Кнопка для вибору папки для тестів
        Button chooseTestsDirButton = new Button("Вибрати папку для тестів");
        chooseTestsDirButton.setOnAction(e -> chooseTestsDirectory());

        // Перший рядок кнопок
        HBox firstButtonRow = new HBox(10);
        firstButtonRow.getChildren().addAll(
                new Label("Кількість запусків:"), runCountSpinner,
                runButton, saveButton, clearButton
        );

        // Другий рядок кнопок
        HBox secondButtonRow = new HBox(10);
        secondButtonRow.getChildren().addAll(chooseTestsDirButton, runAllButton, selectAndRunButton);

        // Рядок для налаштування періодичних тестів
        HBox periodicRow = new HBox(10);
        periodicRow.getChildren().addAll(
                new Label("Інтервал (хв):"), periodicIntervalSpinner,
                new Label("Тривалість (хв):"), periodicDurationSpinner,
                periodicRunButton, stopPeriodicButton
        );

        // Рядок для запланованого виконання
        HBox scheduleRow = new HBox(10);
        scheduleRow.getChildren().addAll(
                new Label("Час початку (HH:mm):"), scheduleStartTimeField,
                scheduleRunButton
        );

        // Поле для відображення результатів тестування
        resultArea = new TextArea();
        resultArea.setPrefWidth(500);
        resultArea.setPrefHeight(200);
        resultArea.setEditable(false);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        // Додавання всіх елементів на панель
        root.getChildren().addAll(
                new Label("URL:"), urlField,
                new Label("Метод:"), methodCombo,
                apiKeyRow,
                new Label("Заголовки:"), headersField,
                new Label("Параметри:"), paramsField,
                new Label("Тіло:"), bodyField,
                firstButtonRow, secondButtonRow,
                new Label("Періодичне виконання:"), periodicRow,
                new Label("Заплановане виконання:"), scheduleRow,
                new Label("Результат:"), resultArea
        );

        return root;
    }

    // Вибір папки для збереження тестів
    private void chooseTestsDirectory() {
        // Налаштування вигляду вибору папки відповідно до системного стилю
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Створення діалогу для вибору папки
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setAcceptAllFileFilterUsed(false);
        folderChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        // Відображення діалогу вибору папки
        int result = folderChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();

            // Перевірка прав доступу до обраної папки
            if (hasFullAccess(selectedFolder)) {
                testsDirectory = selectedFolder.getAbsolutePath();
                resultArea.setText("Обрано папку для тестів: " + testsDirectory);
            } else {
                // Повідомлення про помилку, якщо немає прав доступу
                JOptionPane.showMessageDialog(null,
                        "Немає доступу до папки: " + selectedFolder.getAbsolutePath() +
                                "\nБудь ласка, оберіть іншу папку з правами на читання та запис.",
                        "Помилка доступу",
                        JOptionPane.ERROR_MESSAGE);
                testsDirectory = null;
                resultArea.setText("Помилка: Оберіть папку, до якої є доступ.");
            }
        }
    }

    // Перевірка прав доступу до папки (читання та запис)
    private boolean hasFullAccess(File folder) {
        if (!folder.canRead() || !folder.canWrite()) {
            return false;
        }

        try {
            // Створення тимчасового файлу для перевірки прав
            File tempFile = new File(folder, "temp_access_test.txt");
            if (tempFile.createNewFile()) {
                try (FileWriter writer = new FileWriter(tempFile)) {
                    writer.write("test");
                }
                try (FileReader reader = new FileReader(tempFile)) {
                    reader.read();
                }
                tempFile.delete();
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    // Збереження API ключа у файл
    private void saveApiKey() {
        String apiKey = apiKeyField.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            resultArea.setText("Помилка: введіть API Key перед збереженням.\n");
            return;
        }

        // Створення діалогу для вибору файлу
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Зберегти API Key");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("api-key.txt");
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            if (file.exists() && !file.canWrite()) {
                resultArea.setText("Помилка: немає прав на запис у файл: " + file.getAbsolutePath() + "\n");
                return;
            }

            // Запис API ключа у файл
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(apiKey);
                resultArea.setText("API Key збережено у файл: " + file.getAbsolutePath() + "\n");
            } catch (IOException e) {
                resultArea.setText("Помилка при збереженні API Key: " + e.getMessage() + "\n");
            }
        } else {
            resultArea.setText("Збереження API Key скасовано.\n");
        }
    }

    // Завантаження API ключа із файлу
    private void loadApiKey() {
        // Створення діалогу для вибору файлу
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Завантажити API Key");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            if (!file.canRead()) {
                resultArea.setText("Помилка: немає прав на читання файлу: " + file.getAbsolutePath() + "\n");
                return;
            }

            // Читання API ключа із файлу
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String apiKey = reader.readLine();
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    apiKeyField.setText(apiKey.trim());
                    resultArea.setText("API Key завантажено із файлу: " + file.getAbsolutePath() + "\n");
                } else {
                    resultArea.setText("Помилка: файл API Key порожній.\n");
                }
            } catch (IOException e) {
                resultArea.setText("Помилка при завантаженні API Key: " + e.getMessage() + "\n");
            }
        } else {
            resultArea.setText("Завантаження API Key скасовано.\n");
        }
    }

    // Очищення всіх полів введення
    private void clearFields() {
        urlField.clear();
        methodCombo.setValue("GET");
        apiKeyField.clear();
        headersField.clear();
        paramsField.clear();
        bodyField.clear();
        resultArea.clear();
    }

    // Створення панелі для вкладки "Графіки"
    private VBox createChartPane() {
        VBox chartPane = new VBox(10);
        chartPane.setPadding(new Insets(10));

        // Випадаючий список для вибору історії запитів
        requestSelector = new ComboBox<>();
        requestSelector.setPromptText("Виберіть запит для графіків");
        requestSelector.setPrefWidth(400);
        requestSelector.setOnAction(e -> {
            updateCharts();
            updateTimeMetrics();
            updateSizeMetrics();
        });

        // Налаштування відображення елементів у списку історії
        requestSelector.setCellFactory(listView -> new ComboBoxListCell<>() {
            @Override
            public void updateItem(RequestHistory item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.toString());
                    Tooltip tooltip = new Tooltip(item.toString());
                    setTooltip(tooltip);
                }
            }
        });

        // Налаштування відображення вибраного елемента
        requestSelector.setButtonCell(new ComboBoxListCell<>() {
            @Override
            public void updateItem(RequestHistory item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        // Кнопка для очищення історії запитів
        Button clearHistoryButton = new Button("Очистити історію");
        clearHistoryButton.setOnAction(e -> clearHistory());

        // Кнопка для вибору папки для звітів
        Button chooseReportDirButton = new Button("Вибрати папку для звітів");
        chooseReportDirButton.setOnAction(e -> chooseReportDirectory());

        // Кнопка для генерації звіту
        Button generateReportButton = new Button("Згенерувати звіт");
        generateReportButton.setOnAction(e -> generateReport());

        // Рядок для вибору запиту та очищення історії
        HBox selectorBox = new HBox(10);
        selectorBox.getChildren().addAll(
                new Label("Виберіть запит:"), requestSelector, clearHistoryButton
        );

        // Рядок для вибору папки звітів та генерації звіту
        HBox reportBox = new HBox(10);
        reportBox.getChildren().addAll(chooseReportDirButton, generateReportButton);

        // Графік для часу виконання
        NumberAxis timeXAxis = new NumberAxis();
        timeXAxis.setLabel("Номер виконання");
        NumberAxis timeYAxis = new NumberAxis();
        timeYAxis.setLabel("Час (мс)");
        timeChart = new LineChart<>(timeXAxis, timeYAxis);
        timeChart.setTitle("Час виконання запиту");
        timeChart.setPrefHeight(300);

        // Елементи для вибору метрик часу виконання
        timeMetricsCombo = new ComboBox<>();
        timeMetricsCombo.getItems().addAll(
                "Середній час виконання",
                "Максимальний і мінімальний час виконання",
                "Тренд часу виконання"
        );
        timeMetricsCombo.setValue("Середній час виконання");
        timeMetricsCombo.setOnAction(e -> updateTimeMetrics());

        timeMetricLabel = new Label("Виберіть метрику для відображення");

        HBox timeMetricsBox = new HBox(10);
        timeMetricsBox.getChildren().addAll(
                new Label("Показати: "), timeMetricsCombo, timeMetricLabel
        );

        // Графік для розміру відповіді
        NumberAxis sizeXAxis = new NumberAxis();
        sizeXAxis.setLabel("Номер виконання");
        NumberAxis sizeYAxis = new NumberAxis();
        sizeYAxis.setLabel("Розмір (байти)");
        sizeChart = new LineChart<>(sizeXAxis, sizeYAxis);
        sizeChart.setTitle("Розмір відповіді");
        sizeChart.setPrefHeight(300);

        // Елементи для вибору метрик розміру відповіді
        sizeMetricsCombo = new ComboBox<>();
        sizeMetricsCombo.getItems().addAll(
                "Середній розмір відповіді",
                "Максимальний і мінімальний розмір відповіді",
                "Пропускна здатність",
                "Відсоток змін розміру відповіді"
        );
        sizeMetricsCombo.setValue("Середній розмір відповіді");
        sizeMetricsCombo.setOnAction(e -> updateSizeMetrics());

        sizeMetricLabel = new Label("Виберіть метрику для відображення");

        HBox sizeMetricsBox = new HBox(10);
        sizeMetricsBox.getChildren().addAll(
                new Label("Показати: "), sizeMetricsCombo, sizeMetricLabel
        );

        // Додавання всіх елементів на панель графіків
        chartPane.getChildren().addAll(
                selectorBox, reportBox,
                timeChart, timeMetricsBox,
                sizeChart, sizeMetricsBox
        );

        return chartPane;
    }

    // Вибір папки для збереження звітів
    private void chooseReportDirectory() {
        // Налаштування вигляду вибору папки
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Створення діалогу для вибору папки
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setAcceptAllFileFilterUsed(false);
        folderChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        // Відображення діалогу вибору папки
        int result = folderChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();

            // Перевірка прав доступу до папки
            if (hasFullAccess(selectedFolder)) {
                reportDirectory = selectedFolder.getAbsolutePath();
                resultArea.setText("Директорія для звітів вибрана: " + reportDirectory + "\n");
            } else {
                // Повідомлення про помилку, якщо немає прав доступу
                JOptionPane.showMessageDialog(null,
                        "Немає доступу до папки: " + selectedFolder.getAbsolutePath() +
                                "\nБудь ласка, оберіть іншу папку з правами на читання та запис.",
                        "Помилка доступу",
                        JOptionPane.ERROR_MESSAGE);
                reportDirectory = null;
                resultArea.setText("Помилка: Оберіть папку для звітів, до якої є доступ.");
            }
        } else {
            resultArea.setText("Директорія для звітів не вибрана. Використовується стандартна: " + reportDirectory + "\n");
        }
    }

    // Генерація звіту на основі результатів тесту
    private void generateReport() {
        RequestHistory selectedRequest = requestSelector.getValue();
        if (selectedRequest == null) {
            resultArea.setText("Помилка: виберіть запит для генерації звіту.\n");
            return;
        }

        List<TestResult> results = selectedRequest.getResults();
        if (results.isEmpty()) {
            resultArea.setText("Помилка: немає результатів для цього запиту.\n");
            return;
        }

        if (reportDirectory == null || reportDirectory.isEmpty()) {
            resultArea.setText("Помилка: Спочатку оберіть папку для звітів.\n");
            return;
        }

        // Перевірка папки для звітів
        File reportDir = new File(reportDirectory);
        if (!reportDir.exists()) {
            if (!reportDir.mkdirs()) {
                resultArea.setText("Помилка: не вдалося створити папку для звітів: " + reportDirectory + "\n");
                reportDirectory = null;
                return;
            }
        } else if (!reportDir.isDirectory()) {
            resultArea.setText("Помилка: шлях для звітів не є папкою: " + reportDirectory + "\n");
            reportDirectory = null;
            return;
        } else if (!hasFullAccess(reportDir)) {
            resultArea.setText("Помилка: немає прав на читання або запис у папку для звітів: " + reportDirectory + "\n");
            reportDirectory = null;
            return;
        }

        // Формування імені файлу звіту з унікальним часовим маркером
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        String testName = selectedRequest.getTestCase().method() + "_" +
                selectedRequest.getTestCase().url().replaceAll("[^a-zA-Z0-9]", "_");
        String reportFileName = "TestReport_" + testName + "_" + timestamp + ".txt";
        String reportFilePath = reportDirectory + File.separator + reportFileName;

        File reportFile = new File(reportFilePath);
        if (reportFile.exists() && !reportFile.canWrite()) {
            resultArea.setText("Помилка: немає прав на запис у файл звіту: " + reportFilePath + "\n");
            return;
        }

        checkAndClearLogFile();

        // Запис звіту у файл
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFilePath))) {
            writer.write("Звіт про тестування\n");
            writer.write("-------------------\n");
            writer.write("Дата: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
            writer.write("Тест: " + selectedRequest.toString() + "\n");
            writer.write("Кількість запусків: " + results.size() + "\n\n");

            TestCase testCase = selectedRequest.getTestCase();
            writer.write("Параметри тесту:\n");
            writer.write("Метод: " + testCase.method() + "\n");
            writer.write("URL: " + testCase.url() + "\n");
            writer.write("Параметри: " + (testCase.params().isEmpty() ? "Немає" : testCase.params()) + "\n");
            writer.write("Заголовки: " + (testCase.headers().isEmpty() ? "Немає" : testCase.headers()) + "\n");
            writer.write("Тіло: " + (testCase.body().isEmpty() ? "Немає" : testCase.body()) + "\n\n");

            int successfulTests = 0;
            long totalTime = 0;
            for (int i = 0; i < results.size(); i++) {
                TestResult result = results.get(i);
                writer.write("Запуск " + (i + 1) + ":\n");
                writer.write("Час виконання: " + result.timeTaken() + " мс\n");
                writer.write("Статус-код: " + result.statusCode() + "\n");
                writer.write("Розмір відповіді: " + result.responseSize() + " байт\n");
                String responseBody = result.result();
                if (responseBody.contains("Тіло відповіді: ")) {
                    responseBody = responseBody.split("Тіло відповіді: ")[1].split("\n")[0];
                    if (responseBody.length() > 100) {
                        responseBody = responseBody.substring(0, 100) + " [Скорочено]";
                    }
                    writer.write("Тіло відповіді: " + responseBody + "\n");
                } else {
                    writer.write("Тіло відповіді: [Немає]\n");
                }
                writer.write("Результат: " + (result.result().contains("Тест пройшов успішно") ? "Тест пройшов успішно" : result.result()) + "\n\n");

                if (result.result().contains("Тест пройшов успішно")) {
                    successfulTests++;
                }
                totalTime += result.timeTaken();
            }

            writer.write("Статистика:\n");
            writer.write("Середній час виконання: " + (results.isEmpty() ? 0 : totalTime / results.size()) + " мс\n");
            writer.write("Успішних тестів: " + successfulTests + "\n");
            writer.write("Провальних тестів: " + (results.size() - successfulTests) + "\n");

            resultArea.setText("Звіт згенеровано: " + reportFilePath + "\n");
        } catch (IOException e) {
            resultArea.setText("Помилка при генерації звіту: " + e.getMessage() + "\n");
        }
    }

    // Очищення історії запитів та графіків
    private void clearHistory() {
        requestHistory.clear();
        requestSelector.getItems().clear();
        timeChart.getData().clear();
        sizeChart.getData().clear();
        timeMetricLabel.setText("Виберіть метрику для відображення");
        sizeMetricLabel.setText("Виберіть метрику для відображення");
        periodicRunCount = 0;
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
        if (scheduleTimer != null) {
            scheduleTimer.cancel();
            scheduleTimer = null;
        }
    }

    // Оновлення графіків на основі обраного запиту
    private void updateCharts() {
        RequestHistory selectedRequest = requestSelector.getValue();
        if (selectedRequest == null) return;

        List<TestResult> results = selectedRequest.getResults();
        if (results.isEmpty()) return;

        // Вибір останніх результатів для відображення
        int displayCount = selectedRequest.getDisplayRunCount();
        int startIndex = Math.max(0, results.size() - displayCount);
        List<TestResult> displayResults = results.subList(startIndex, results.size());

        // Оновлення графіка часу виконання
        timeChart.getData().clear();
        XYChart.Series<Number, Number> timeSeries = new XYChart.Series<>();
        timeSeries.setName("Час виконання");
        for (int i = 0; i < displayResults.size(); i++) {
            timeSeries.getData().add(new XYChart.Data<>(startIndex + i + 1, displayResults.get(i).timeTaken()));
        }
        timeChart.getData().add(timeSeries);

        // Додавання підказок до точок на графіку
        for (XYChart.Data<Number, Number> data : timeSeries.getData()) {
            Tooltip tooltip = new Tooltip(data.getYValue().toString() + " мс");
            if (data.getNode() != null) {
                Tooltip.install(data.getNode(), tooltip);
            }
        }

        // Оновлення графіка розміру відповіді
        sizeChart.getData().clear();
        XYChart.Series<Number, Number> sizeSeries = new XYChart.Series<>();
        sizeSeries.setName("Розмір відповіді");
        for (int i = 0; i < displayResults.size(); i++) {
            sizeSeries.getData().add(new XYChart.Data<>(startIndex + i + 1, displayResults.get(i).responseSize()));
        }
        sizeChart.getData().add(sizeSeries);

        // Додавання підказок до точок на графіку
        for (XYChart.Data<Number, Number> data : sizeSeries.getData()) {
            Tooltip tooltip = new Tooltip(data.getYValue().toString() + " байт");
            if (data.getNode() != null) {
                Tooltip.install(data.getNode(), tooltip);
            }
        }
    }

    // Оновлення метрик для графіка "Час виконання"
    private void updateTimeMetrics() {
        RequestHistory selectedRequest = requestSelector.getValue();
        if (selectedRequest == null || selectedRequest.getResults().isEmpty()) {
            timeMetricLabel.setText("Немає даних для обраного запиту");
            return;
        }

        List<TestResult> results = selectedRequest.getResults();
        int displayCount = selectedRequest.getDisplayRunCount();
        int startIndex = Math.max(0, results.size() - displayCount);
        List<TestResult> displayResults = results.subList(startIndex, results.size());

        String selectedMetric = timeMetricsCombo.getValue();
        switch (selectedMetric) {
            case "Середній час виконання":
                double avgTime = displayResults.stream().mapToLong(TestResult::timeTaken).average().orElse(0);
                timeMetricLabel.setText(String.format("Середній час: %.1f мс (Загальна швидкість API)", avgTime));
                break;
            case "Максимальний і мінімальний час виконання":
                long maxTime = displayResults.stream().mapToLong(TestResult::timeTaken).max().orElse(0);
                long minTime = displayResults.stream().mapToLong(TestResult::timeTaken).min().orElse(0);
                timeMetricLabel.setText(String.format("Макс: %d мс, Мін: %d мс (Діапазон швидкості)", maxTime, minTime));
                break;
            case "Тренд часу виконання":
                if (displayResults.size() < 2) {
                    timeMetricLabel.setText("Тренд: Недостатньо даних (Потрібно ≥2 запусків)");
                } else {
                    long firstTime = displayResults.get(0).timeTaken();
                    long lastTime = displayResults.get(displayResults.size() - 1).timeTaken();
                    String trend = lastTime > firstTime ? "Зростає (Можливе перевантаження)" :
                            lastTime < firstTime ? "Зменшується (Оптимізація)" : "Стабільний";
                    timeMetricLabel.setText(String.format("Тренд: %s", trend));
                }
                break;
        }
    }

    // Оновлення метрик для графіка "Розмір відповіді"
    private void updateSizeMetrics() {
        RequestHistory selectedRequest = requestSelector.getValue();
        if (selectedRequest == null || selectedRequest.getResults().isEmpty()) {
            sizeMetricLabel.setText("Немає даних для обраного запиту");
            return;
        }

        List<TestResult> results = selectedRequest.getResults();
        int displayCount = selectedRequest.getDisplayRunCount();
        int startIndex = Math.max(0, results.size() - displayCount);
        List<TestResult> displayResults = results.subList(startIndex, results.size());

        String selectedMetric = sizeMetricsCombo.getValue();
        switch (selectedMetric) {
            case "Середній розмір відповіді":
                double avgSize = displayResults.stream().mapToLong(TestResult::responseSize).average().orElse(0);
                sizeMetricLabel.setText(String.format("Середній розмір: %.1f байт (Типовий обсяг даних)", avgSize));
                break;
            case "Максимальний і мінімальний розмір відповіді":
                long maxSize = displayResults.stream().mapToLong(TestResult::responseSize).max().orElse(0);
                long minSize = displayResults.stream().mapToLong(TestResult::responseSize).min().orElse(0);
                sizeMetricLabel.setText(String.format("Макс: %d байт, Мін: %d байт (Діапазон обсягу)", maxSize, minSize));
                break;
            case "Пропускна здатність":
                double avgsize = displayResults.stream().mapToLong(TestResult::responseSize).average().orElse(0);
                double avgTime = displayResults.stream().mapToLong(TestResult::timeTaken).average().orElse(0);
                double throughput = avgTime > 0 ? (avgsize / (avgTime / 1000.0)) : 0;
                sizeMetricLabel.setText(String.format("Пропускна здатність: %.1f байт/с (Ефективність передачі)", throughput));
                break;
            case "Відсоток змін розміру відповіді":
                if (displayResults.size() < 2) {
                    sizeMetricLabel.setText("Відсоток змін: Недостатньо даних (Потрібно ≥2 запусків)");
                } else {
                    long firstSize = displayResults.get(0).responseSize();
                    long lastSize = displayResults.get(displayResults.size() - 1).responseSize();
                    double changePercent = firstSize != 0 ? ((double) (lastSize - firstSize) / firstSize) * 100 : 0;
                    sizeMetricLabel.setText(String.format("Відсоток змін: %.1f%% (Варіація даних)", changePercent));
                }
                break;
        }
    }

    // Додавання результату тесту до історії запитів
    private void addToHistory(TestCase testCase, TestResult testResult, int displayRunCount) {
        // Пошук існуючого запису в історії за параметрами запиту
        RequestHistory history = requestHistory.stream()
                .filter(h -> h.getTestCase().url().equals(testCase.url()) &&
                        h.getTestCase().method().equals(testCase.method()) &&
                        h.getTestCase().params().equals(testCase.params()))
                .findFirst()
                .orElse(null);

        // Якщо запису немає, створюємо новий
        if (history == null) {
            history = new RequestHistory(testCase, displayRunCount);
            requestHistory.add(history);
            requestSelector.getItems().add(history);
        }

        // Додавання результату до історії
        history.addResult(testResult);

        // Оновлення графіків, якщо цей запит обраний
        if (requestSelector.getValue() == history) {
            Platform.runLater(() -> {
                updateCharts();
                updateTimeMetrics();
                updateSizeMetrics();
            });
        }
    }

    // Виконання одного тесту
    private void runSingleTest() {
        // Створення тест-кейсу з введених даних
        TestCase testCase = new TestCase(
                urlField.getText(),
                methodCombo.getValue(),
                headersField.getText(),
                paramsField.getText(),
                bodyField.getText(),
                "200"
        );

        String apiKey = apiKeyField.getText();
        int runCount = runCountSpinner.getValue();

        checkAndClearLogFile();

        // Виконання тесту задану кількість разів
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < runCount; i++) {
            TestResult testResult = TestRunner.runTest(testCase, apiKey);
            results.append(String.format("Запуск %d:\n%s\n\n", i + 1, testResult.result()));
            addToHistory(testCase, testResult, runCount);
        }

        resultArea.setText(results.toString());
    }

    // Виконання періодичних тестів
    private void runPeriodicTest() {
        // Створення тест-кейсу з введених даних
        TestCase testCase = new TestCase(
                urlField.getText(),
                methodCombo.getValue(),
                headersField.getText(),
                paramsField.getText(),
                bodyField.getText(),
                "200"
        );

        String apiKey = apiKeyField.getText();
        int intervalMinutes = periodicIntervalSpinner.getValue();
        int durationMinutes = periodicDurationSpinner.getValue();
        int expectedRunCount = durationMinutes / intervalMinutes;
        periodicRunCount = 0;

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long durationMillis = durationMinutes * 60 * 1000L;

        // Зупинка попереднього таймера, якщо він існує
        if (periodicTimer != null) {
            periodicTimer.cancel();
        }
        periodicTimer = new Timer();

        checkAndClearLogFile();

        // Завдання для періодичного виконання
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                periodicRunCount++;
                checkAndClearLogFile();
                TestResult testResult = TestRunner.runTest(testCase, apiKey);
                Platform.runLater(() -> {
                    addToHistory(testCase, testResult, expectedRunCount);
                    resultArea.appendText(String.format("Періодичний запуск %d (на %d хвилині):\n%s\n\n", periodicRunCount, periodicRunCount * intervalMinutes, testResult.result()));
                });
            }
        };

        // Запуск періодичних тестів
        periodicTimer.scheduleAtFixedRate(task, intervalMillis, intervalMillis);

        // Завдання для зупинки періодичних тестів після закінчення тривалості
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                periodicTimer.cancel();
                periodicTimer = null;
                Platform.runLater(() -> resultArea.appendText("Періодичне виконання завершено.\n"));
            }
        }, durationMillis);

        resultArea.setText("Періодичне виконання розпочато: кожні " + intervalMinutes + " хвилин протягом " + durationMinutes + " хвилин. Очікувана кількість запусків: " + expectedRunCount + ". Перший запуск через " + intervalMinutes + " хвилин.\n");
    }

    // Зупинка періодичних тестів
    private void stopPeriodicTest() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
            resultArea.appendText("Періодичні запити зупинено.\n");
        } else {
            resultArea.appendText("Періодичні запити не запущені.\n");
        }
    }

    // Заплановане виконання тесту
    private void scheduleTest() {
        // Створення тест-кейсу з введених даних
        TestCase testCase = new TestCase(
                urlField.getText(),
                methodCombo.getValue(),
                headersField.getText(),
                paramsField.getText(),
                bodyField.getText(),
                "200"
        );

        String apiKey = apiKeyField.getText();
        String startTimeStr = scheduleStartTimeField.getText();
        LocalTime startTime;

        // Перевірка введення часу
        if (startTimeStr == null || startTimeStr.trim().isEmpty()) {
            resultArea.setText("Помилка: поле часу не може бути порожнім. Використовуйте HH:mm (наприклад, 14:30).");
            return;
        }

        if (!startTimeStr.matches("\\d{2}:\\d{2}")) {
            resultArea.setText("Помилка: неправильний формат часу. Використовуйте HH:mm (наприклад, 14:30).");
            return;
        }

        // Парсинг часу
        try {
            startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            resultArea.setText("Помилка: неправильний формат часу. Використовуйте HH:mm (наприклад, 14:30).");
            return;
        }

        // Перевірка коректності введених годин і хвилин
        int hours = startTime.getHour();
        int minutes = startTime.getMinute();
        if (hours < 0 || hours > 23) {
            resultArea.setText("Помилка: години мають бути в межах 00–23.");
            return;
        }
        if (minutes < 0 || minutes > 59) {
            resultArea.setText("Помилка: хвилини мають бути в межах 00–59.");
            return;
        }

        // Обчислення затримки до запланованого часу
        LocalDateTime startDateTime = LocalDateTime.of(LocalDate.now(), startTime);
        long delay = Duration.between(LocalDateTime.now(), startDateTime).toMillis();
        if (delay < 0) {
            startDateTime = startDateTime.plusDays(1);
            delay = Duration.between(LocalDateTime.now(), startDateTime).toMillis();
        }

        // Зупинка попереднього таймера, якщо він існує
        if (scheduleTimer != null) {
            scheduleTimer.cancel();
        }
        scheduleTimer = new Timer();

        checkAndClearLogFile();

        // Завдання для запланованого виконання
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                TestResult testResult = TestRunner.runTest(testCase, apiKey);
                Platform.runLater(() -> {
                    addToHistory(testCase, testResult, 1);
                    resultArea.appendText(String.format("Запланований запуск:\n%s\n\n", testResult.result()));
                });
                scheduleTimer.cancel();
                scheduleTimer = null;
            }
        };

        // Запуск завдання у визначений час
        scheduleTimer.schedule(task, delay);
        resultArea.setText("Заплановане виконання заплановано на " + startTime.format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
    }

    // Збереження тест-кейсу у файл
    private void saveTestCase() {
        if (testsDirectory == null || testsDirectory.isEmpty()) {
            resultArea.setText("Помилка: Спочатку оберіть папку для тестів.\n");
            return;
        }

        // Перевірка папки для тестів
        File testsDir = new File(testsDirectory);
        if (!testsDir.exists()) {
            if (!testsDir.mkdirs()) {
                resultArea.setText("Помилка: не вдалося створити папку для тестів: " + testsDirectory + "\n");
                testsDirectory = null;
                return;
            }
        } else if (!testsDir.isDirectory()) {
            resultArea.setText("Помилка: шлях для тестів не є папкою: " + testsDirectory + "\n");
            testsDirectory = null;
            return;
        } else if (!hasFullAccess(testsDir)) {
            resultArea.setText("Помилка: немає прав на читання або запис у папку для тестів: " + testsDirectory + "\n");
            testsDirectory = null;
            return;
        }

        // Створення тест-кейсу
        TestCase testCase = new TestCase(
                urlField.getText(),
                methodCombo.getValue(),
                headersField.getText(),
                paramsField.getText(),
                bodyField.getText(),
                "200"
        );
        testCases.add(testCase);
        try {
            TestGenerator.saveTestCase(testCase, testsDirectory);
            resultArea.setText("Тест збережено у папку: " + testsDirectory + "\n");
        } catch (RuntimeException e) {
            resultArea.setText("Помилка при збереженні тесту: " + e.getMessage() + "\n");
        }
    }

    // Виконання всіх збережених тестів
    private void runAllTests() {
        File testsDir = new File(testsDirectory);
        if (!testsDir.exists() || !testsDir.isDirectory()) {
            resultArea.setText("Помилка: папка для тестів не існує або не є папкою: " + testsDirectory + "\n");
            return;
        } else if (!testsDir.canRead()) {
            resultArea.setText("Помилка: немає прав на читання папки для тестів: " + testsDirectory + "\n");
            return;
        }

        // Завантаження всіх тест-кейсів
        testCases.clear();
        testCases.addAll(TestGenerator.loadTestCases(testsDirectory));

        String apiKey = apiKeyField.getText();
        int runCount = runCountSpinner.getValue();

        checkAndClearLogFile();

        // Виконання всіх тестів
        StringBuilder results = new StringBuilder();
        for (TestCase testCase : testCases) {
            for (int i = 0; i < runCount; i++) {
                TestResult testResult = TestRunner.runTest(testCase, apiKey);
                results.append(String.format("Запуск %d для %s %s:\n%s\n\n", i + 1, testCase.method(), testCase.url(), testResult.result()));
                addToHistory(testCase, testResult, runCount);
            }
        }
        resultArea.setText(results.toString());
    }

    // Виконання вибраного тесту з файлу
    private void runSelectedTest() {
        File testsDir = new File(testsDirectory);
        if (!testsDir.exists() || !testsDir.isDirectory()) {
            resultArea.setText("Помилка: папка для тестів не існує або не є папкою: " + testsDirectory + "\n");
            return;
        } else if (!testsDir.canRead()) {
            resultArea.setText("Помилка: немає прав на читання папки для тестів: " + testsDirectory + "\n");
            return;
        }

        // Створення діалогу для вибору тестового файлу
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Вибрати тестовий файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File testsFolder = new File(testsDirectory);
        if (testsFolder.exists() && testsFolder.isDirectory()) {
            fileChooser.setInitialDirectory(testsFolder);
        }
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            if (!selectedFile.canRead()) {
                resultArea.setText("Помилка: немає прав на читання файлу: " + selectedFile.getAbsolutePath() + "\n");
                return;
            }

            // Завантаження тесту з файлу
            try {
                ObjectMapper mapper = new ObjectMapper();
                TestCase testCase = mapper.readValue(selectedFile, TestCase.class);

                // Заповнення полів введення даними з тесту
                urlField.setText(testCase.url());
                methodCombo.setValue(testCase.method());
                headersField.setText(testCase.headers());
                paramsField.setText(testCase.params());
                bodyField.setText(testCase.body());

                String apiKey = apiKeyField.getText();
                int runCount = runCountSpinner.getValue();

                checkAndClearLogFile();

                // Виконання тесту
                StringBuilder results = new StringBuilder();
                for (int i = 0; i < runCount; i++) {
                    TestResult testResult = TestRunner.runTest(testCase, apiKey);
                    results.append(String.format("Запуск %d:\n%s\n\n", i + 1, testResult.result()));
                    addToHistory(testCase, testResult, runCount);
                }
                resultArea.setText(results.toString());
            } catch (IOException e) {
                resultArea.setText("Помилка при завантаженні тесту: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            resultArea.setText("Файл не вибрано.");
        }
    }

    // Точка входу програми
    public static void main(String[] args) {
        launch(args);
    }
}