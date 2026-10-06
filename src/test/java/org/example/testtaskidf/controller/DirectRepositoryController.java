package org.example.testtaskidf.controller;

import org.example.testtaskidf.repository.ArchitectureFixtureRepository;

// Deliberate violation used only by ArchitectureRulesTests, never imported as production code.
public class DirectRepositoryController {
    public String read(ArchitectureFixtureRepository repository) {
        return repository.read();
    }
}
