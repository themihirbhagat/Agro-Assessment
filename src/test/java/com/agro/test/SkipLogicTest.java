package com.agro.test;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SkipLogicTest {
	
    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeClass
    public void setup() {
        // Initialize Selenium WebDriver
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        
        // Navigate to the web-based Survey Builder preview or a rendered form page
        driver.get("https://builder.yourdomain.com/surveyBuilder");
    }

    @Test(description = "Verify 'What is your hobby?' appears only when 'Do you have a hobby?' equals 'Yes'")
    public void testDynamicVisibilityLogic() {
        
    	// For demonstration purpose POM design is not followed here otherwise locators would go in a dedicated Page class.  
        By hobbyExistOrNotDropdown = By.cssSelector("[data-question-id='q_3'] select");	
        
        By whatIsYourHobbyText = By.cssSelector("[data-question-id='q_4']"); 
        
        By whatIsYourHobbyTextbox = By.cssSelector("[data-question-id='q_4'] input");

        // Verify initially 'What is your hobby' question is hidden.
        boolean isHiddenInitially = driver.findElements(whatIsYourHobbyText).isEmpty() || 
                                    !driver.findElement(whatIsYourHobbyText).isDisplayed();
        
        Assert.assertTrue(isHiddenInitially, "Hobby text field should be hidden by default");

        // 2. Trigger the condition: Select "Yes" from the Dropdown (q_3)
        WebElement dropdownElement = wait.until(ExpectedConditions.visibilityOfElementLocated(hobbyExistOrNotDropdown));
        
        Select hobbyDropdown = new Select(dropdownElement);
        
        hobbyDropdown.selectByVisibleText("Yes"); 

        // 3. Assert the dependent question (q_4) dynamically appears
        WebElement dependentInput = wait.until(ExpectedConditions.visibilityOfElementLocated(whatIsYourHobbyTextbox));
        
        Assert.assertTrue(dependentInput.isDisplayed(), "whatIsYourHobbyTextbox must become visible when 'Yes' is selected");

        // 4. Trigger the reverse condition: Select "No"
        hobbyDropdown.selectByVisibleText("No"); 

        // 5. Assert the dependent question (q_4) disappears again
        Boolean isHiddenAgain = wait.until(ExpectedConditions.invisibilityOfElementLocated(whatIsYourHobbyText));
        
        Assert.assertTrue(isHiddenAgain, "whatIsYourHobbyTextbox must hide when 'No' is selected");
    }

    @AfterClass
    public void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
