# Морковки на связи — Android 1.8.1

Восстановленный Android-проект со всеми исходными файлами: MainActivity.java, assets/chat.html, Gradle Wrapper, ресурсы и интеграции.

## Две сборки в GitHub Actions

- **Morkovki-IP-HTTP-Test-Debug-APK** открывает `http://212.220.99.132:5761/`. Нативный Google Sign-in и Android JS-мост отключены: HTTP не защищает токены и личные данные.
- **Morkovki-Website-HTTPS-Debug-APK** открывает `https://morkovkionline.ru/` с прежними Android-функциями и Google Credential Manager. Серверная интеграция OAuth должна быть корректно настроена.

Сохранены поддержка загрузок/вложений, системное меню «Поделиться», полноэкранное видео, разрешения для HTTPS-камеры/микрофона и исходный встроенный демонстрационный HTML-чат.

## Локальная сборка

Требуется JDK 17, Android SDK 35. Вариант сайта по умолчанию использует HTTPS.

```bash
./gradlew --no-daemon assembleDebug "-PsiteUrl=http://212.220.99.132:5761/"
./gradlew --no-daemon assembleDebug "-PsiteUrl=https://morkovkionline.ru/"
```

Каждая команда перезаписывает `app/build/outputs/apk/debug/app-debug.apk`.

**Важно:** IP-тест передаёт данные без шифрования. Не вводите настоящие пароли, токены или личные сообщения. Для рабочего приложения настройте HTTPS. WebView требует HTML-фронтенд: если порт 5761 отдаёт только JSON API, нужен другой стартовый URL или отдельный API-клиент.

**Подпись:** файл `signing/android-debug.keystore` не публикуется. APK с подписью GitHub Actions может потребовать удаления старой тестовой версии перед установкой (при удалении теряются локальные данные).
