package com.novabank.generator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "generator")
public class GeneratorProperties {

    private int customerCount = 50;
    private BaseUrl baseUrl = new BaseUrl();

    public int getCustomerCount() {
        return customerCount;
    }

    public void setCustomerCount(int customerCount) {
        this.customerCount = customerCount;
    }

    public BaseUrl getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(BaseUrl baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static class BaseUrl {
        private String customerService = "http://localhost:8081";
        private String accountService = "http://localhost:8082";
        private String cardService = "http://localhost:8083";

        public String getCustomerService() {
            return customerService;
        }

        public void setCustomerService(String customerService) {
            this.customerService = customerService;
        }

        public String getAccountService() {
            return accountService;
        }

        public void setAccountService(String accountService) {
            this.accountService = accountService;
        }

        public String getCardService() {
            return cardService;
        }

        public void setCardService(String cardService) {
            this.cardService = cardService;
        }
    }
}
