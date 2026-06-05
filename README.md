# Ochotka 1.0

Ochotka is an Android application designed to support restaurant and dish discovery through a map-first experience.  
The project focuses on searching for dishes rather than only venues, and presenting relevant restaurants directly on the map together with contextual dish results.

The application was developed in collaboration with [@sylwiarozek](https://github.com/sylwiarozek).

## Overview

Ochotka combines dish search, category-based filtering and map exploration into a single mobile experience.  
Instead of treating restaurants as the primary search object, the app is centered around food intent: users search for what they want to eat, and the interface returns restaurants that can satisfy that need.

Core interaction flow:

- search by dish name or food-related phrase
- filter results by category, such as `Pizza`, `Sushi` or `Ramen`
- display matching restaurants as map markers
- open a restaurant marker to see matched dishes available in that location
- navigate to dish details or the full restaurant menu
- save favorite restaurants for quick access later

## Key Features

- map-based home screen with dish-oriented discovery
- category filters integrated directly into the main search flow
- one marker per restaurant with grouped matched dishes
- restaurant detail view with full menu access
- dish detail view with ingredients, variants and pricing
- favorites module stored locally on device
- local user profile with editable name and profile image

## Tech Stack

- Kotlin
- Android SDK
- MVVM architecture
- Navigation Component
- LiveData / StateFlow
- Firebase Firestore
- Google Maps SDK
- SharedPreferences

## Architecture

The application follows a `single-activity` architecture with Navigation Component-based screen management.

Main layers:

- `ui/` – fragments, adapters and presentation logic
- `data/` – models and repository layer
- `common/` – shared utilities and query interpretation logic

Design principles used in the project:

- `MVVM` for clear separation between UI and business logic
- `Repository` abstraction for data access
- `Fragment-based navigation` within a single `MainActivity`
- local UI state preservation for search and map interactions

## Data Layer

Ochotka uses Firebase Firestore as its primary data source.

Current Firestore structure:

- `restaurants`
- `search_index`
- `restaurants/{restaurantId}/dishes`

The `search_index` collection is used as a lightweight lookup layer for fast dish search and map result generation, while restaurant subcollections provide full menu data for detail screens.

## Search Model

The search experience is dish-first.

This means:

- queries are matched primarily against dish data
- restaurants appear as grouped results for matching dishes
- a single restaurant marker may represent multiple matched dishes
- users can move from a marker to either dish details or the restaurant menu

The project also includes lightweight query interpretation logic for handling more natural food-related input and multi-item queries.

## Project Status

Version: `1.0`

This release represents the first complete project version, developed as an academic mobile application with an emphasis on architecture, usability and scalable data organization.

## Running the Project

1. Clone the repository.
2. Open the project in Android Studio.
3. Add local configuration files:
   - copy `local.properties.example` to `local.properties`
   - provide your own `MAPS_API_KEY`
   - add your own `app/google-services.json`
4. Build and run the application on an emulator or physical Android device.



