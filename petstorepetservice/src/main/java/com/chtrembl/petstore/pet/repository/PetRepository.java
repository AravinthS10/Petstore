package com.chtrembl.petstore.pet.repository;

import com.chtrembl.petstore.pet.dto.Pet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {}
