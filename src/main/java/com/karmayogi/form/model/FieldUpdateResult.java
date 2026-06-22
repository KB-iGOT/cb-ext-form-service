package com.karmayogi.form.model;

import java.util.Set;

public record FieldUpdateResult(int createdCount,
                                int updatedCount,
                                int deletedCount,
                                Set<String> staleFieldIds) {
}
