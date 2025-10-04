# Настройка Google Maps API

## Получение API ключа

1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте новый проект или выберите существующий
3. Включите следующие API:
   - Maps SDK for Android
   - Places API (опционально)
4. Перейдите в "Credentials" → "Create Credentials" → "API Key"
5. Скопируйте полученный ключ

## Настройка приложения

1. Откройте файл `app/src/main/AndroidManifest.xml`
2. Найдите строку:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```
3. Замените `YOUR_API_KEY_HERE` на ваш реальный API ключ

## Ограничения API ключа (рекомендуется)

1. В Google Cloud Console перейдите к вашему API ключу
2. Нажмите "Edit" (карандаш)
3. В разделе "Application restrictions" выберите "Android apps"
4. Добавьте ваш package name: `com.bebraradar`
5. Добавьте SHA-1 fingerprint вашего debug keystore

## Получение SHA-1 fingerprint

```bash
# Для debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# Для release keystore (когда будете публиковать)
keytool -list -v -keystore your-release-key.keystore -alias your-key-alias
```

## Тестирование

После настройки API ключа:
1. Соберите приложение: `./gradlew assembleDebug`
2. Установите на устройство: `./gradlew installDebug`
3. Запустите приложение и проверьте отображение карты
