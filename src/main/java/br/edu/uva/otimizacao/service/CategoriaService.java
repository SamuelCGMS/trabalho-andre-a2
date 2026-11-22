package br.edu.uva.otimizacao.service;

import br.edu.uva.otimizacao.dto.CategoriaEstatisticasDTO;
import br.edu.uva.otimizacao.repository.CategoriaEstatisticasRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoriaService {

    private final CategoriaEstatisticasRepository repo;

    public CategoriaService(CategoriaEstatisticasRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public CategoriaEstatisticasDTO estatisticas(Long id) {
        return repo.calcular(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria nao encontrada: " + id));
    }
}
