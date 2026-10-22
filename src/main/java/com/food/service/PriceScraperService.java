
package com.food.service;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PriceScraperService {

    private static final Map<String, String> DISH_IDS = Map.of(
        "pizza", "68987",
        "burger", "9055",
        "biryani", "30308",
        "pasta", "51177",
        "dosa", "10296",
        "idli", "7078",
        "noodles", "19315",
        "rolls", "56691",
        "paneer", "11035"
    );

    public Map<String, Object> getPrices(String item) {
        Map<String, Object> result = new HashMap<>();
        result.put("item", item);
        WebDriver driver = null;

        try {
            // Configure Chrome options
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));

            // Auto-grant Geolocation permission
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("profile.default_content_setting_values.geolocation", 1);
            options.setExperimentalOption("prefs", prefs);

            // Set to the locally downloaded, compatible ChromeDriver version 149
            System.setProperty("webdriver.chrome.driver", new java.io.File("drivers/chromedriver.exe").getAbsolutePath());
            ChromeDriver chromeDriver = new ChromeDriver(options);
            driver = chromeDriver;

            // Set geolocation override via CDP to Kadubeesanahalli, Bangalore
            Map<String, Object> coordinates = new HashMap<>();
            coordinates.put("latitude", 12.9352);
            coordinates.put("longitude", 77.6984);
            coordinates.put("accuracy", 100);
            chromeDriver.executeCdpCommand("Emulation.setGeolocationOverride", coordinates);

            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");

            String cleanItem = item.toLowerCase().trim();
            
            // Scrape both platforms
            Map<String, Object> zomatoInfo = scrapeZomato(driver, js, cleanItem);
            Map<String, Object> swiggyInfo = scrapeSwiggy(driver, js, cleanItem);
            
            result.put("zomato_data", zomatoInfo);
            result.put("swiggy_data", swiggyInfo);
            
            // Compare prices if available
            String zomatoPrice = (String) zomatoInfo.get("price");
            String swiggyPrice = (String) swiggyInfo.get("price");
            
            if (zomatoPrice != null && swiggyPrice != null) {
                result.put("price_comparison", comparePrices(zomatoPrice, swiggyPrice));
            }

        } catch (Exception e) {
            result.put("error", "Scraping failed: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        return result;
    }
    
    private Map<String, Object> scrapeZomato(WebDriver driver, JavascriptExecutor js, String dishName) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // Create search URL
            String searchUrl = DISH_IDS.containsKey(dishName)
                ? "https://www.zomato.com/bangalore/kadubeesanahalli-restaurants?dishv2_id=" + DISH_IDS.get(dishName) + "&category=1"
                : "https://www.zomato.com/search?query=" + URLEncoder.encode(dishName, StandardCharsets.UTF_8);
            
            driver.get(searchUrl);
            info.put("url", searchUrl);
            
            // Wait and scroll
            Thread.sleep(2000);
            js.executeScript("window.scrollBy(0, 300)");
            
            // Find restaurant cards
            List<WebElement> restaurantCards = driver.findElements(By.cssSelector("[data-testid='restaurant-card'], .jumbo-tracker"));
            
            if (!restaurantCards.isEmpty()) {
                WebElement firstCard = restaurantCards.get(0);
                String cardText = firstCard.getText();
                
                // Extract restaurant name (non-price, non-numeric text)
                for (String line : cardText.split("\\n")) {
                    if (!line.contains("₹") && !line.matches(".*[0-9]\\.[0-9].*") && 
                        !line.matches(".*[0-9]+ min.*") && line.length() > 3) {
                        info.put("name", line.trim());
                        break;
                    }
                }
                
                // Extract data using regex
                extractDataWithRegex(cardText, info);
            }
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    private void setLocationOnSwiggy(WebDriver driver) {
        try {
            driver.get("https://www.swiggy.com/");
            Thread.sleep(3000);
            
            List<WebElement> locationInputs = driver.findElements(By.id("location"));
            if (!locationInputs.isEmpty()) {
                WebElement input = locationInputs.get(0);
                input.click();
                input.sendKeys("Kadubeesanahalli, Bangalore");
                Thread.sleep(2000);
                
                List<WebElement> suggestions = driver.findElements(By.cssSelector("div._2BgUI, div[class*='suggestion'], [class*='search-result']"));
                if (!suggestions.isEmpty()) {
                    suggestions.get(0).click();
                    Thread.sleep(3000);
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting Swiggy location: " + e.getMessage());
        }
    }

    private Map<String, Object> scrapeSwiggy(WebDriver driver, JavascriptExecutor js, String dishName) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // Setup location on Swiggy homepage
            setLocationOnSwiggy(driver);

            String searchUrl = "https://www.swiggy.com/search?query=" + 
                              URLEncoder.encode(dishName, StandardCharsets.UTF_8);
            
            driver.get(searchUrl);
            info.put("url", searchUrl);
            Thread.sleep(4000);
            
            // ── 1. Extract restaurant name ──────────────────────────────────
            // Try heading-level elements (h2, h3) for the restaurant name
            List<String> restaurantNameCandidates = new ArrayList<>();
            for (WebElement el : driver.findElements(By.cssSelector("h2, h3, [class*='RestaurantHeader'], [class*='restaurant-name']")))
            {
                String t = el.getText().trim();
                if (!t.isEmpty() && !t.equalsIgnoreCase("Ad")) {
                    restaurantNameCandidates.add(t);
                }
            }
            if (!restaurantNameCandidates.isEmpty()) {
                info.put("name", restaurantNameCandidates.get(0));
            }

            // ── 2. Extract actual item prices — skip promo labels ──────────
            // Swiggy shows promo as "ITEMS AT ₹59"; real prices are inside
            // individual menu item cards (span/div containing only a ₹ price)
            Pattern itemPricePattern = Pattern.compile("^[₹]([0-9,]+)$");
            List<WebElement> priceEls = driver.findElements(
                By.cssSelector("[class*='price'], [class*='Price'], [class*='cost'], span"));
            for (WebElement el : priceEls) {
                String t = el.getText().trim();
                // skip crossed-out / discounted old price (has strikethrough siblings)
                // accept only clean "₹NNN" tokens that are ≥ 100 (avoid promo ₹59 etc.)
                Matcher m = itemPricePattern.matcher(t);
                if (m.matches()) {
                    String numStr = m.group(1).replace(",", "");
                    try {
                        int val = Integer.parseInt(numStr);
                        if (val >= 100) {          // real menu prices are usually ≥ ₹100
                            info.put("price", t);
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // ── 3. Fallback: scan card text, but skip "ITEMS AT" lines ─────
            if (!info.containsKey("price")) {
                List<WebElement> cards = driver.findElements(By.cssSelector(
                    "[data-testid*='restaurant'], [class*='restaurant'], [class*='card']"));
                if (!cards.isEmpty()) {
                    String cardText = cards.get(0).getText();
                    extractSwiggyData(cardText, info);
                }
            }

            // ── 4. Also pull rating & delivery time from body text ─────────
            String pageText = driver.findElement(By.tagName("body")).getText();
            extractSwiggyData(pageText, info);

        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        
        return info;
    }

    /**
     * Swiggy-specific data extractor that:
     * - Strips "ITEMS AT" promo lines (e.g. "ITEMS AT ₹59")
     * - Strips "Less than Rs" filter chip lines (e.g. "Less than Rs 800")
     * - Uses ₹-only price regex (filter chips use "Rs NNN", real prices use "₹NNN")
     * - Only accepts prices ≥ ₹100 to avoid badge/promo values
     */
    private void extractSwiggyData(String text, Map<String, Object> info) {
        String cleaned = Arrays.stream(text.split("\n"))
            .filter(line -> !line.toUpperCase().contains("ITEMS AT"))
            .filter(line -> !line.toUpperCase().contains("LESS THAN"))
            .reduce("", (a, b) -> a + "\n" + b);

        // Price: ₹-only regex, skip values < 100
        if (!info.containsKey("price")) {
            Pattern pricePattern = Pattern.compile("₹([0-9,]+)");
            Matcher priceMatcher = pricePattern.matcher(cleaned);
            while (priceMatcher.find()) {
                try {
                    int val = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                    if (val >= 100) {
                        info.put("price", "₹" + priceMatcher.group(1));
                        break;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Rating
        if (!info.containsKey("rating")) {
            Pattern ratingPattern = Pattern.compile("([0-9]\\.[0-9])");
            Matcher ratingMatcher = ratingPattern.matcher(cleaned);
            if (ratingMatcher.find()) info.put("rating", ratingMatcher.group());
        }

        // Delivery time
        if (!info.containsKey("delivery_time")) {
            Pattern timePattern = Pattern.compile("([0-9]+-?[0-9]*)\\s*(min|mins|MIN|MINS)");
            Matcher timeMatcher = timePattern.matcher(cleaned);
            if (timeMatcher.find()) info.put("delivery_time", timeMatcher.group());
        }
    }
    
    private void extractDataWithRegex(String text, Map<String, Object> info) {
        // Price pattern
        Pattern pricePattern = Pattern.compile("(₹|Rs\\.?\\s?)([0-9,]+)");
        Matcher priceMatcher = pricePattern.matcher(text);
        if (priceMatcher.find() && !info.containsKey("price")) {
            info.put("price", priceMatcher.group());
        }
        
        // Rating pattern (4.5)
        Pattern ratingPattern = Pattern.compile("([0-9]\\.[0-9])");
        Matcher ratingMatcher = ratingPattern.matcher(text);
        if (ratingMatcher.find() && !info.containsKey("rating")) {
            info.put("rating", ratingMatcher.group());
        }
        
        // Delivery time pattern
        Pattern timePattern = Pattern.compile("([0-9]+-?[0-9]*)\\s*(min|mins|MIN|MINS)");
        Matcher timeMatcher = timePattern.matcher(text);
        if (timeMatcher.find() && !info.containsKey("delivery_time")) {
            info.put("delivery_time", timeMatcher.group());
        }
    }
    
    private Map<String, Object> comparePrices(String zomatoPrice, String swiggyPrice) {
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("zomato_price", zomatoPrice);
        comparison.put("swiggy_price", swiggyPrice);
        
        try {
            double zomatoValue = parsePrice(zomatoPrice);
            double swiggyValue = parsePrice(swiggyPrice);
            
            double difference = Math.abs(zomatoValue - swiggyValue);
            double percentDiff = (difference / Math.max(zomatoValue, swiggyValue)) * 100;
            
            comparison.put("price_difference", String.format("₹%.2f", difference));
            comparison.put("percentage_difference", String.format("%.2f%%", percentDiff));
            
            if (zomatoValue < swiggyValue) {
                comparison.put("cheaper_platform", "Zomato");
            } else if (swiggyValue < zomatoValue) {
                comparison.put("cheaper_platform", "Swiggy");
            } else {
                comparison.put("conclusion", "Same price on both platforms");
            }
            
            if (zomatoValue != swiggyValue) {
                comparison.put("savings", String.format("₹%.2f", difference));
            }
        } catch (Exception e) {
            comparison.put("error", "Could not compare prices: " + e.getMessage());
        }
        
        return comparison;
    }
    
    private double parsePrice(String priceStr) {
        return Double.parseDouble(priceStr.replaceAll("[^0-9.]", ""));
    }
}

