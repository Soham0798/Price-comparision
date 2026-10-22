# 🍕 Food Price Comparison — Zomato vs Swiggy

A Spring Boot web application that compares food prices between **Zomato** and **Swiggy** in real-time using Selenium web scraping.

## Features

- 🔍 Search for any food item and compare prices across platforms
- 📊 Side-by-side comparison with ratings and delivery times
- 🏆 Highlights the cheaper option with savings amount
- 🎨 Clean, responsive Bootstrap UI
- 🔒 Secure — input validation, rate limiting, security headers

## Tech Stack

| Layer       | Technology              |
|-------------|------------------------|
| Backend     | Spring Boot 3.1.5      |
| Templating  | Thymeleaf              |
| Scraping    | Selenium + ChromeDriver|
| Frontend    | Bootstrap 5            |
| Java        | 17+                    |

## Prerequisites

- **Java 17+**
- **Maven 3.9+**
- **Google Chrome** (installed on the system)
- **ChromeDriver** — download the version matching your Chrome and place it in a `drivers/` folder at the project root

## Getting Started

```bash
# Clone the repository
git clone https://github.com/Soham0798/Price-comparision.git
cd Price-comparision

# Download ChromeDriver (must match your Chrome version)
# Place chromedriver.exe in ./drivers/

# Run the application
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

## Security

This application includes the following security measures:

- **Input Validation** — strict regex whitelist on search queries (alphanumeric, max 50 chars)
- **Rate Limiting** — 30 requests/minute per IP address
- **Security Headers** — CSP, X-Frame-Options, X-Content-Type-Options, XSS protection
- **Error Handling** — generic error messages to clients; full details logged server-side only
- **Server Fingerprint Removal** — server header suppressed
- **.gitignore** — prevents secrets, binaries, and build artifacts from being committed

## Project Structure

```
├── src/main/java/com/food/
│   ├── PriceComparatorApplication.java   # Spring Boot entry point
│   ├── config/
│   │   ├── SecurityFilter.java           # Rate limiting & security headers
│   │   └── GlobalExceptionHandler.java   # Safe error responses
│   ├── controller/
│   │   └── PriceController.java          # Web & API endpoints
│   └── service/
│       └── PriceScraperService.java      # Selenium scraping logic
├── src/main/resources/
│   ├── application.properties
│   └── templates/
│       ├── index.html                    # Search page
│       └── compare.html                  # Results page
├── drivers/                              # ChromeDriver (not committed)
├── pom.xml
└── .gitignore
```

## API

| Endpoint        | Method | Parameters | Description                    |
|-----------------|--------|------------|--------------------------------|
| `/`             | GET    | —          | Home / search page             |
| `/compare`      | GET    | `item`     | Compare prices (HTML view)     |
| `/api/compare`  | GET    | `item`     | Compare prices (JSON response) |

## License

This project is for educational purposes.
