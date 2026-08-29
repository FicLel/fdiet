package com.fdiet.food.dto;

/**
 * Just enough of a food to rank it as a suggestion. The whole table is 957
 * rows, so all of them fit in memory and a candidate search costs no query at
 * all — which is the point: a {@code LIKE '%…%'} per unmatched ingredient is
 * the scan this avoids.
 */
public record BedcaNameRow(Long id, String name, String foodGroup) {
}
