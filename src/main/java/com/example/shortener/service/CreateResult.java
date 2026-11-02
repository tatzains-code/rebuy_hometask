package com.example.shortener.service;

import com.example.shortener.entity.ShortUrl;

public record CreateResult(ShortUrl shortUrl, boolean created) {
}
