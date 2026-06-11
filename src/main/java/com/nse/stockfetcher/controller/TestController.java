package com.nse.stockfetcher.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nse.stockfetcher.exception.StockNotFoundException;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, NSE Stock Fetcher!";
    }

    @GetMapping("/world")
    public String world() {
        throw new StockNotFoundException("test exception message");
    }

}
