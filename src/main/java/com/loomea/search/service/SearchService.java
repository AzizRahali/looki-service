package com.loomea.search.service;

import com.loomea.salon.dto.SalonDto;
import com.loomea.salon.repository.SalonRepository;
import com.loomea.salon.service.SalonServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final SalonRepository salonRepository;
    private final SalonServiceImpl salonMapper;

    @Transactional(readOnly = true)
    public List<SalonDto> search(String query) {
        if (query == null || query.isBlank()) {
            return salonRepository.findAll().stream().map(salonMapper::toDto).toList();
        }
        return salonRepository.search(query.trim()).stream().map(salonMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonDto> byCategory(String category) {
        return salonRepository.findByServiceCategory(category).stream().map(salonMapper::toDto).toList();
    }
}
