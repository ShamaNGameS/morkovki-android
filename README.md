# Морковки Онлайн — Android 1.8.5

Android-приложение для `https://morkovkionline.ru/chat`, с поддержкой существующих способов входа сайта (логин/пароль, вход старого аккаунта по почте и Google OAuth).

## Собрать APK в GitHub

Actions → **Android APK - Google HTTPS** → успешный запуск → **Morkovki-Google-HTTPS-Debug-APK** → скачайте архив, распакуйте APK и установите его.

GitHub Actions теперь собирает **только HTTPS-вариант**. IP/HTTP-вариант временно отложен, но исходный код сетевых настроек не удалён.

Для локальной сборки: `./gradlew --no-daemon assembleDebug "-PsiteUrl=https://morkovkionline.ru/"`.

## Google и почта

- В `MainActivity` есть поддержка Google Credential Manager (выбор аккаунта Google на Android).
- Нативное окно Google используется **только если сайт предоставляет** `__morkovkiNativeGoogleStart()` или `__morkovkiNativeGoogleComplete(idToken)` и реализует безопасную серверную обработку токена и создание сессии по HTTPS. Без этого доступен существующий веб-вход Google, который может открыть браузер.
- Android не проверяет пароль или email сам: логин по почте, серверное подтверждение, создание сессии и сброс пароля реализуются сервером сайта.
- Сессионные cookie сохраняются по возможности, WebView поддерживает Android Autofill для почты и пароля.
- Отдельная нативная кнопка входа скрыта, если мост сайта не реализован или пользователь уже вошёл. Она не перекрывает интерфейс друзей и чата.

## Экран

Сохраняются исправления v1.8.3 для клавиатуры (IME), узких портретных экранов и safe areas. Требуется проверка на настоящих устройствах.

## Безопасность и обновления

HTTP не используется для авторизации. Не храните ключ подписи APK в GitHub. Если GitHub Actions выпустил APK с другой отладочной подписью, установка поверх старого приложения может не пройти; перед удалением старого приложения сохраните нужные локальные данные.

Исходники сервера сайта не входят в репозиторий `morkovki-android`, поэтому сборка Android APK не доказывает работоспособность серверной Google OAuth-интеграции.


## Вход Google: как исправить код ошибки при запуске выбора аккаунта

В версии 1.8.5 для явного нажатия «Войти через Google» используется `GetSignInWithGoogleOption` вместо `GetGoogleIdOption`. Ошибки Credential Manager теперь показывают короткий диагностический код (например `GetCredentialCustomException`) без раскрытия ID-токена. Полный класс/тип ошибки есть в Android Logcat под `MorkovkiGoogle`; токены не логируются.

**Для работы Google-входа одного APK недостаточно.** В Google Cloud > Google Auth Platform > Clients нужен Android OAuth-клиент с пакетом `ru.morkovki.chat` и **SHA-1 именно установленного APK**. Используемый в Android `google_server_client_id` должен совпадать с Web Client ID серверной авторизации; backend должен проверять ID-токен, nonce и создавать сессию только через HTTPS.

GitHub Actions теперь вкладывает в артефакт APK файл `android-oauth-fingerprint.txt` со SHA-1 отладочной подписи. У GitHub runner отладочный ключ меняется между сборками. Поэтому **для стабильного SHA-1** можно один раз создать отдельный тестовый debug keystore (alias `androiddebugkey`, пароль `android`, требуемый Gradle), затем сохранить его *base64-содержимое* как секрет GitHub Actions с точным именем `MORKOVKI_DEBUG_KEYSTORE_BASE64`. Пример для локального компьютера с JDK:
```bash
mkdir -p signing
keytool -genkeypair -storetype PKCS12 -keystore signing/android-debug.keystore -alias androiddebugkey -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=Morkovki Android Debug,O=Morkovki" -storepass android -keypass android
base64 -w0 signing/android-debug.keystore
```
На Windows PowerShell содержимое base64: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('signing/android-debug.keystore'))`. Добавить в GitHub: Settings > Secrets and variables > Actions > New repository secret. **Никогда не коммитьте keystore или base64 в исходники.** После установки секрета выполнить сборку, взять стабильный SHA-1 из её артефакта и зарегистрировать в Google Cloud. Для опубликованного приложения понадобится отдельный защищённый release key.
