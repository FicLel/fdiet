package com.fdiet.diet.service;

import com.fdiet.diet.domain.Diet;
import com.fdiet.diet.dto.DietDay;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DietService {

    public List<DietDay> create(List<DietDay> days) {
        return new Diet(days).days();
    }
}
