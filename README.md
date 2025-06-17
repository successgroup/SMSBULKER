# SMS Bulker Application

## Project Description

SMS Bulker is an Android application designed to facilitate the sending of bulk SMS messages. It allows users to compose messages, manage recipients, handle skipped contacts, and view analytics related to message sending. The application integrates with a backend API for SMS delivery and provides features for contact management, template creation, and user authentication.

## Features

-   **Bulk SMS Sending**: Compose and send messages to multiple recipients simultaneously.
-   **Recipient Management**: Add recipients manually, import from contacts, or from CSV files.
-   **Skipped Contacts Handling**: Automatically identifies and manages invalid or duplicate contacts, providing a dedicated interface to review and export them.
-   **Message Templates**: Create, save, and reuse message templates for quick message composition.
-   **Contact Management**: View, edit, and delete contacts.
-   **User Authentication**: Secure login and registration for users.
-   **Analytics**: View statistics and reports on message sending, including success and failure rates.
-   **CSV Import/Export**: Import recipients from CSV files and export skipped contacts to CSV.
-   **Theming**: Supports light and dark themes.

## Technologies Used

-   **Kotlin**: Primary programming language.
-   **Android Jetpack**: Comprehensive suite of libraries to help developers follow best practices, reduce boilerplate code, and write code that works consistently across Android versions and devices.
    -   **Architecture Components**: ViewModel, LiveData, Navigation Component, Room Database.
    -   **Data Binding/View Binding**: For efficient UI updates.
-   **Material Design**: For a modern and consistent user interface.
-   **Retrofit**: For networking and API communication.
-   **Dagger Hilt**: For dependency injection.
-   **Firebase**: For analytics and potentially other services.
-   **Gradle Kotlin DSL**: For build configuration.

## Installation

To set up and run the SMS Bulker application locally, follow these steps:

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-repo/SMSBULKER.git
    cd SMSBULKER
    ```

2.  **Open in Android Studio**: Open the cloned project in Android Studio.

3.  **Configure Firebase**: 
    -   Create a Firebase project in the Firebase Console.
    -   Add an Android app to your Firebase project and follow the instructions to download `google-services.json`.
    -   Place the `google-services.json` file in the `app/` directory of your project.

4.  **Configure API Endpoints**: The application communicates with a backend API. Ensure your API endpoints are correctly configured in the relevant `ApiClient` or `NetworkModule` files. You might need to create a `local.properties` file or similar for sensitive API keys/base URLs.

5.  **Build the project**: Sync the Gradle project and build it.
    ```bash
    ./gradlew build
    ```

6.  **Run on a device or emulator**: Connect an Android device or start an emulator and run the application from Android Studio.

## Usage

1.  **Login/Register**: Upon launching the app, you will be prompted to log in or register a new account.
2.  **Compose Message**: Navigate to the home screen to compose your SMS message.
3.  **Add Recipients**: Use the "Select Recipients" button to add contacts from your phone, or import from a CSV file.
4.  **Manage Skipped Contacts**: If any contacts are invalid or duplicates, they will be listed under "Skipped Contacts". You can review and export this list.
5.  **Send SMS**: Once recipients are added and the message is composed, send the bulk SMS.
6.  **View Analytics**: Check the analytics section to see the delivery status of your messages.

## Project Structure

```
SMSBULKER/
├── app/                                # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/                   # Kotlin source code
│   │   │   │   └── com/gscube/smsbulker/
│   │   │   │       ├── data/           # Data models, API interfaces, local database, repositories
│   │   │   │       ├── di/             # Dependency Injection modules (Dagger Hilt)
│   │   │   │       ├── repository/     # Data repositories for various features
│   │   │   │       ├── service/        # Background services (e.g., webhook handling)
│   │   │   │       ├── ui/             # UI components (fragments, activities, viewmodels, adapters)
│   │   │   │       │   ├── account/
│   │   │   │       │   ├── analytics/
│   │   │   │       │   ├── auth/
│   │   │   │       │   ├── base/
│   │   │   │       │   ├── contacts/
│   │   │   │       │   ├── csvEditor/
│   │   │   │       │   ├── home/
│   │   │   │       │   ├── sendMessage/
│   │   │   │       │   ├── settings/
│   │   │   │       │   ├── sms/
│   │   │   │       │   ├── splash/
│   │   │   │       │   └── templates/
│   │   │   │       └── utils/          # Utility classes (permissions, network, validation)
│   │   │   └── res/                    # Android resources (layouts, drawables, values, etc.)
│   │   │       ├── anim/
│   │   │       ├── color/
│   │   │       ├── drawable/
│   │   │       ├── font/
│   │   │       ├── layout/
│   │   │       ├── menu/
│   │   │       ├── mipmap-anydpi-v26/
│   │   │       ├── navigation/
│   │   │       ├── values/
│   │   │       └── xml/
│   │   └── AndroidManifest.xml         # Application manifest
│   ├── build.gradle.kts                # Module-level Gradle build file
│   └── google-services.json            # Firebase configuration file
├── build.gradle.kts                    # Project-level Gradle build file
├── gradle/                             # Gradle wrapper files
├── .gitignore                          # Git ignore file
├── README.md                           # Project documentation (this file)
└── settings.gradle.kts                 # Gradle settings file
```

This structure provides a clear separation of concerns, making the codebase modular and maintainable.