package com.chtrembl.petstore.pet.service;

import com.chtrembl.petstore.pet.model.Pet;
import com.chtrembl.petstore.pet.model.Category;
import com.chtrembl.petstore.pet.model.Tag;
import com.chtrembl.petstore.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;

    public List<Pet> findPetsByStatus(List<String> status) {
        log.info("Finding pets with status: {}", status);
        return petRepository.findAll().stream()
                .filter(petDto -> status.stream().anyMatch(s -> s.equalsIgnoreCase(petDto.getStatus())))
                .map(this::mapToModelPet)
                .toList();
    }

    public Optional<Pet> findPetById(Long petId) {
        log.info("Finding pet with id: {}", petId);
        return petRepository.findById(petId)
                .map(this::mapToModelPet);
    }

    public List<Pet> getAllPets() {
        log.info("Getting all pets");
        return petRepository.findAll().stream()
                .map(this::mapToModelPet)
                .toList();
    }

    public int getPetCount() {
        return (int) petRepository.count();
    }

    private Pet mapToModelPet(com.chtrembl.petstore.pet.dto.Pet dto) {
        if (dto == null) return null;
        return Pet.builder()
                .id(dto.getId())
                .name(dto.getName())
                .category(mapToModelCategory(dto.getCategory()))
                .photoURL(dto.getPhotoURL())
                .tags(dto.getTags() != null ? dto.getTags().stream().map(this::mapToModelTag).toList() : null)
                .status(mapToModelStatus(dto.getStatus()))
                .build();
    }

    private Category mapToModelCategory(com.chtrembl.petstore.pet.dto.Category dto) {
        if (dto == null) return null;
        return Category.builder()
                .id(dto.getId())
                .name(dto.getName())
                .build();
    }

    private Tag mapToModelTag(com.chtrembl.petstore.pet.dto.Tag dto) {
        if (dto == null) return null;
        return Tag.builder()
                .id(dto.getId())
                .name(dto.getName())
                .build();
    }

    private Pet.Status mapToModelStatus(String status) {
        if (status == null) return null;
        try {
            return Pet.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown status: {}", status);
            return null;
        }
    }
}