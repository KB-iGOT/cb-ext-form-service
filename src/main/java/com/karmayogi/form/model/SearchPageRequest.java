package com.karmayogi.form.model;

public record SearchPageRequest(int page,
                                int size,
                                String error) {
}
