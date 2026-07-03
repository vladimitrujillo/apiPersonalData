package mx.personas.api.codigopostal.service;

import mx.personas.api.codigopostal.dto.CodigoPostalResponseDTO;
import mx.personas.api.codigopostal.dto.ColoniaBusquedaDTO;
import mx.personas.api.codigopostal.dto.ColoniaDTO;
import mx.personas.api.codigopostal.model.CpCatalogo;
import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CodigoPostalService {

    private final CpCatalogoRepository cpCatalogoRepository;

    public CodigoPostalService(CpCatalogoRepository cpCatalogoRepository) {
        this.cpCatalogoRepository = cpCatalogoRepository;
    }

    @Cacheable("codigosPostales")
    public CodigoPostalResponseDTO consultarPorCodigoPostal(String codigoPostal) {
        List<CpCatalogo> filas = cpCatalogoRepository.findByCodigoPostal(codigoPostal);
        if (filas.isEmpty()) {
            throw new RecursoNoEncontradoException(ErrorCode.CP_NO_ENCONTRADO,
                    "No existe un código postal '" + codigoPostal + "' en el catálogo");
        }

        CpCatalogo primera = filas.get(0);
        List<ColoniaDTO> colonias = filas.stream()
                .map(fila -> new ColoniaDTO(fila.getAsentamiento(), fila.getTipoAsentamiento()))
                .toList();

        return new CodigoPostalResponseDTO(codigoPostal, primera.getEstado(), primera.getMunicipio(), colonias);
    }

    public List<ColoniaBusquedaDTO> buscarColonias(String nombre, String estado, String municipio) {
        return cpCatalogoRepository.buscarPorNombreParcial(nombre, normalizar(estado), normalizar(municipio))
                .stream()
                .map(fila -> new ColoniaBusquedaDTO(
                        fila.getCodigoPostal(), fila.getEstado(), fila.getMunicipio(),
                        fila.getAsentamiento(), fila.getTipoAsentamiento()))
                .toList();
    }

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }
}
