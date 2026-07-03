package mx.personas.api.persona.controller;

import jakarta.validation.Valid;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.service.PersonaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/personas")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @PostMapping
    public ResponseEntity<PersonaResponseDTO> crear(@Valid @RequestBody PersonaRequestDTO request) {
        PersonaResponseDTO creada = personaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonaResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(personaService.obtenerPorId(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PersonaResponseDTO> actualizar(@PathVariable UUID id,
                                                           @Valid @RequestBody PersonaUpdateDTO request) {
        return ResponseEntity.ok(personaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        personaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
