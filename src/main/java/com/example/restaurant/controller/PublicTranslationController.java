package com.example.restaurant.controller;

import com.example.restaurant.dto.translation.PublicMenuTranslationResponse;
import com.example.restaurant.service.PublicTranslationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-translations")
public class PublicTranslationController {
    private final PublicTranslationService publicTranslationService;

    public PublicTranslationController(PublicTranslationService publicTranslationService) {
        this.publicTranslationService = publicTranslationService;
    }

    @GetMapping("/menu")
    public PublicMenuTranslationResponse translateMenu(
            @RequestParam(name = "lang", defaultValue = "en") String language) {
        return publicTranslationService.translateMenu(language);
    }
}
