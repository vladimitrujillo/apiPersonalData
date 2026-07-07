package mx.personas.api.persona;

import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.common.error.ApiException;
import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.PersonaYaActivaException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.dto.CampoCambiadoDTO;
import mx.personas.api.persona.dto.DireccionDTO;
import mx.personas.api.persona.dto.DireccionUpdateDTO;
import mx.personas.api.persona.dto.HistorialPageResponseDTO;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.mapper.PersonaMapper;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.model.PersonaHistorial;
import mx.personas.api.persona.model.PersonaHistorial.TipoOperacion;
import mx.personas.api.persona.repository.DireccionRepository;
import mx.personas.api.persona.repository.PersonaHistorialRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import mx.personas.api.persona.service.DireccionValidada;
import mx.personas.api.persona.service.DireccionValidationService;
import mx.personas.api.persona.service.HistorialDiffService;
import mx.personas.api.persona.service.PersonaService;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la orquestación de PersonaService (crear/actualizar/eliminar/restaurar/historial):
 * cuándo se persiste una entrada de historial, con qué operación y qué usuario_id, la
 * regla de unicidad de CURP (global, distingue activo/eliminado) y las validaciones de
 * restaurar/historial (FR-002 a FR-011). Ningún @WebMvcTest ejercita esta lógica real
 * (mockean PersonaService por completo).
 */
@ExtendWith(MockitoExtension.class)
class PersonaServiceTest {

    @Mock
    private PersonaRepository personaRepository;

    @Mock
    private DireccionRepository direccionRepository;

    @Mock
    private PersonaMapper personaMapper;

    @Mock
    private DireccionValidationService direccionValidationService;

    @Mock
    private PersonaHistorialRepository personaHistorialRepository;

    @Mock
    private HistorialDiffService historialDiffService;

    @Mock
    private SecurityAuditorAware securityAuditorAware;

    @Mock
    private UsuarioRepository usuarioRepository;

    private PersonaService personaService() {
        return new PersonaService(personaRepository, direccionRepository, personaMapper, direccionValidationService,
                personaHistorialRepository, historialDiffService, securityAuditorAware, usuarioRepository);
    }

    private Persona personaDeEjemplo() {
        return new Persona("Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678");
    }

    private Direccion direccionDeEjemplo(Persona persona) {
        return new Direccion(persona, "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc",
                "Ciudad de México", "06700", "MX", 1L);
    }

    private DireccionValidada validadaDeEjemplo() {
        return new DireccionValidada("Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX", 1L);
    }

    private void mockearAutorActual(UUID usuarioId) {
        lenient().when(securityAuditorAware.getCurrentAuditor()).thenReturn(Optional.of(usuarioId));
    }

    private PersonaRequestDTO requestDeEjemplo() {
        DireccionDTO direccionDTO = new DireccionDTO("Av. Insurgentes", "100", "Roma Norte", null, null,
                "06700", "MX");
        return new PersonaRequestDTO("Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678", direccionDTO);
    }

    // ---------- crear ----------

    @Test
    void crearPersistePersonaDireccionYEntradaDeHistorialCreacion() {
        UUID usuarioId = UUID.randomUUID();
        mockearAutorActual(usuarioId);
        Persona persona = personaDeEjemplo();
        PersonaRequestDTO dto = requestDeEjemplo();

        when(personaRepository.existsByCorreoAndActivoTrue(dto.correo())).thenReturn(false);
        when(personaRepository.findByCurp(dto.curp())).thenReturn(Optional.empty());
        when(personaMapper.toEntity(dto)).thenReturn(persona);
        when(direccionValidationService.validarYCompletar(any(), any(), any(), any(), any()))
                .thenReturn(validadaDeEjemplo());
        when(historialDiffService.serializarCreacion(eq(persona), any())).thenReturn("[]");
        lenient().when(personaMapper.toResponseDTO(any(), any())).thenReturn(mockRespuesta());

        personaService().crear(dto);

        ArgumentCaptor<PersonaHistorial> captor = ArgumentCaptor.forClass(PersonaHistorial.class);
        verify(personaHistorialRepository).save(captor.capture());
        assertThat(captor.getValue().getOperacion()).isEqualTo(TipoOperacion.CREACION);
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(captor.getValue().getPersona()).isEqualTo(persona);
    }

    @Test
    void crearConCurpDeRegistroActivoLanza409Duplicado() {
        PersonaRequestDTO dto = requestDeEjemplo();
        Persona otraActiva = personaDeEjemplo();
        ReflectionTestUtils.setField(otraActiva, "id", UUID.randomUUID());
        when(personaRepository.existsByCorreoAndActivoTrue(dto.correo())).thenReturn(false);
        when(personaRepository.findByCurp(dto.curp())).thenReturn(Optional.of(otraActiva));

        assertThatThrownBy(() -> personaService().crear(dto))
                .isInstanceOf(DuplicateFieldException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_CURP_DUPLICADO);
    }

    @Test
    void crearConCurpDeRegistroEliminadoLanza409AccionableConIdDelEliminado() {
        PersonaRequestDTO dto = requestDeEjemplo();
        Persona eliminada = personaDeEjemplo();
        eliminada.eliminarLogicamente();
        ReflectionTestUtils.setField(eliminada, "id", UUID.randomUUID());
        when(personaRepository.existsByCorreoAndActivoTrue(dto.correo())).thenReturn(false);
        when(personaRepository.findByCurp(dto.curp())).thenReturn(Optional.of(eliminada));

        assertThatThrownBy(() -> personaService().crear(dto))
                .isInstanceOf(DuplicateFieldException.class)
                .satisfies(ex -> {
                    var apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.PERSONA_CURP_ELIMINADA);
                    assertThat(apiEx.getDetalles().get(0).motivo()).contains(String.valueOf(eliminada.getId()));
                });
    }

    // ---------- actualizar ----------

    @Test
    void actualizarSoloDireccionGeneraEntradaDeHistorialModificacion() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        mockearAutorActual(usuarioId);
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        when(personaRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(persona));
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona)).thenReturn(Optional.of(direccion));
        DireccionUpdateDTO nuevaDireccion = new DireccionUpdateDTO("Otra Calle", null, null, null, null, null, null);
        PersonaUpdateDTO dto = new PersonaUpdateDTO(null, null, null, null, null, null, null, null, nuevaDireccion);
        when(direccionValidationService.validarYCompletar(any(), any(), any(), any(), any()))
                .thenReturn(validadaDeEjemplo());
        when(historialDiffService.serializarModificacion(any(), eq(persona), any(), eq(direccion)))
                .thenReturn(Optional.of("[{\"campo\":\"direccion.calle\"}]"));
        lenient().when(personaMapper.toResponseDTO(any(), any())).thenReturn(mockRespuesta());

        personaService().actualizar(id, dto);

        ArgumentCaptor<PersonaHistorial> captor = ArgumentCaptor.forClass(PersonaHistorial.class);
        verify(personaHistorialRepository).save(captor.capture());
        assertThat(captor.getValue().getOperacion()).isEqualTo(TipoOperacion.MODIFICACION);
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(usuarioId);
        verify(direccionRepository).save(direccion);
        verify(personaRepository, never()).findByCurp(any());
    }

    @Test
    void actualizarSinCambiosRealesNoPersisteNingunaEntradaDeHistorial() {
        UUID id = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        when(personaRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(persona));
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona)).thenReturn(Optional.of(direccion));
        PersonaUpdateDTO dto = new PersonaUpdateDTO(null, null, null, null, null, null, null, null, null);
        when(historialDiffService.serializarModificacion(any(), eq(persona), any(), eq(direccion)))
                .thenReturn(Optional.empty());
        lenient().when(personaMapper.toResponseDTO(any(), any())).thenReturn(mockRespuesta());

        personaService().actualizar(id, dto);

        verify(personaHistorialRepository, never()).save(any());
    }

    @Test
    void actualizarConCurpDeOtroRegistroActivoLanza409Duplicado() {
        UUID id = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        when(personaRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(persona));
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona))
                .thenReturn(Optional.of(direccionDeEjemplo(persona)));
        Persona otraActiva = personaDeEjemplo();
        ReflectionTestUtils.setField(otraActiva, "id", UUID.randomUUID());
        when(personaRepository.findByCurp("OTRA900101HDFRZN01")).thenReturn(Optional.of(otraActiva));
        PersonaUpdateDTO dto = new PersonaUpdateDTO(null, null, null, null, "OTRA900101HDFRZN01", null, null, null,
                null);

        assertThatThrownBy(() -> personaService().actualizar(id, dto))
                .isInstanceOf(DuplicateFieldException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_CURP_DUPLICADO);
    }

    @Test
    void actualizarConCurpDeRegistroEliminadoLanza409AccionableConId() {
        UUID id = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        when(personaRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(persona));
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona))
                .thenReturn(Optional.of(direccionDeEjemplo(persona)));
        Persona eliminada = personaDeEjemplo();
        eliminada.eliminarLogicamente();
        ReflectionTestUtils.setField(eliminada, "id", UUID.randomUUID());
        when(personaRepository.findByCurp("ELIM900101HDFRZN01")).thenReturn(Optional.of(eliminada));
        PersonaUpdateDTO dto = new PersonaUpdateDTO(null, null, null, null, "ELIM900101HDFRZN01", null, null, null,
                null);

        assertThatThrownBy(() -> personaService().actualizar(id, dto))
                .isInstanceOf(DuplicateFieldException.class)
                .satisfies(ex -> {
                    var apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.PERSONA_CURP_ELIMINADA);
                    assertThat(apiEx.getDetalles().get(0).motivo()).contains(String.valueOf(eliminada.getId()));
                });
    }

    @Test
    void actualizarConLaMismaCurpQueYaTeniaNoValidaNiLanza() {
        UUID id = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        when(personaRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(persona));
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona))
                .thenReturn(Optional.of(direccionDeEjemplo(persona)));
        when(historialDiffService.serializarModificacion(any(), eq(persona), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(personaMapper.toResponseDTO(any(), any())).thenReturn(mockRespuesta());
        PersonaUpdateDTO dto = new PersonaUpdateDTO(null, null, null, null, persona.getCurp(), null, null, null,
                null);

        personaService().actualizar(id, dto);

        verify(personaRepository, never()).findByCurp(any());
    }

    // ---------- eliminar ----------

    @Test
    void eliminarPersisteEntradaDeHistorialEliminacion() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        mockearAutorActual(usuarioId);
        Persona persona = personaDeEjemplo();
        when(personaRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(persona));
        when(historialDiffService.serializarCambioEstadoActivo(true, false)).thenReturn("[]");

        personaService().eliminar(id);

        assertThat(persona.isActivo()).isFalse();
        ArgumentCaptor<PersonaHistorial> captor = ArgumentCaptor.forClass(PersonaHistorial.class);
        verify(personaHistorialRepository).save(captor.capture());
        assertThat(captor.getValue().getOperacion()).isEqualTo(TipoOperacion.ELIMINACION);
    }

    // ---------- restaurar ----------

    @Test
    void restaurarConIdInexistenteLanza404() {
        UUID id = UUID.randomUUID();
        when(personaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personaService().restaurar(id))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_NO_ENCONTRADA);
    }

    @Test
    void restaurarPersonaYaActivaLanza409() {
        UUID id = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        when(personaRepository.findById(id)).thenReturn(Optional.of(persona));

        assertThatThrownBy(() -> personaService().restaurar(id))
                .isInstanceOf(PersonaYaActivaException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_YA_ACTIVA);
    }

    @Test
    void restaurarConCorreoYaTomadoPorOtraPersonaActivaLanza409ConIdYNoAlteraNada() {
        UUID id = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        persona.eliminarLogicamente();
        Persona activaConElCorreo = personaDeEjemplo();
        when(personaRepository.findById(id)).thenReturn(Optional.of(persona));
        when(personaRepository.findByCorreoAndActivoTrue(persona.getCorreo()))
                .thenReturn(Optional.of(activaConElCorreo));

        assertThatThrownBy(() -> personaService().restaurar(id))
                .isInstanceOf(DuplicateFieldException.class)
                .satisfies(ex -> {
                    var apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.PERSONA_CORREO_DUPLICADO);
                    assertThat(apiEx.getDetalles().get(0).motivo())
                            .contains(String.valueOf(activaConElCorreo.getId()));
                });

        // SC-006: el intento fallido no debe alterar el registro (sigue eliminada).
        assertThat(persona.isActivo()).isFalse();
        verify(personaHistorialRepository, never()).save(any());
    }

    @Test
    void restaurarNuncaConsultaCurpPorqueEsGloballyUnica() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        mockearAutorActual(usuarioId);
        Persona persona = personaDeEjemplo();
        persona.eliminarLogicamente();
        Direccion direccion = direccionDeEjemplo(persona);
        when(personaRepository.findById(id)).thenReturn(Optional.of(persona));
        when(personaRepository.findByCorreoAndActivoTrue(persona.getCorreo())).thenReturn(Optional.empty());
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona)).thenReturn(Optional.of(direccion));
        when(historialDiffService.serializarCambioEstadoActivo(false, true)).thenReturn("[]");
        lenient().when(personaMapper.toResponseDTO(any(), any())).thenReturn(mockRespuesta());

        personaService().restaurar(id);

        // FR-010: restaurar NUNCA debe rechazarse por CURP - el service ni siquiera la consulta.
        verify(personaRepository, never()).findByCurp(any());
    }

    @Test
    void restaurarExitosoMarcaActivaYPersisteEntradaDeHistorialRestauracion() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        mockearAutorActual(usuarioId);
        Persona persona = personaDeEjemplo();
        persona.eliminarLogicamente();
        Direccion direccion = direccionDeEjemplo(persona);
        when(personaRepository.findById(id)).thenReturn(Optional.of(persona));
        when(personaRepository.findByCorreoAndActivoTrue(persona.getCorreo())).thenReturn(Optional.empty());
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona)).thenReturn(Optional.of(direccion));
        when(historialDiffService.serializarCambioEstadoActivo(false, true)).thenReturn("[]");
        lenient().when(personaMapper.toResponseDTO(any(), any())).thenReturn(mockRespuesta());

        personaService().restaurar(id);

        assertThat(persona.isActivo()).isTrue();
        ArgumentCaptor<PersonaHistorial> captor = ArgumentCaptor.forClass(PersonaHistorial.class);
        verify(personaHistorialRepository).save(captor.capture());
        assertThat(captor.getValue().getOperacion()).isEqualTo(TipoOperacion.RESTAURACION);
    }

    // ---------- historial ----------

    @Test
    void historialConIdInexistenteLanza404() {
        UUID id = UUID.randomUUID();
        when(personaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personaService().historial(id, PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PERSONA_NO_ENCONTRADA);
    }

    @Test
    void historialMapeaCadaEntradaResolviendoElLoginDelAutor() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Persona persona = personaDeEjemplo();
        Usuario autor = new Usuario("admin", "hash", "Admin", Rol.ADMIN);
        PersonaHistorial entrada = new PersonaHistorial(persona, usuarioId, TipoOperacion.CREACION, "[]");
        Pageable pageable = PageRequest.of(0, 20);
        Page<PersonaHistorial> pagina = new PageImpl<>(List.of(entrada), pageable, 1);

        when(personaRepository.findById(id)).thenReturn(Optional.of(persona));
        when(personaHistorialRepository.findByPersonaOrderByFechaDesc(persona, pageable)).thenReturn(pagina);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(autor));
        when(historialDiffService.deserializar("[]"))
                .thenReturn(List.of(new CampoCambiadoDTO("nombres", null, "Juana")));

        HistorialPageResponseDTO respuesta = personaService().historial(id, pageable);

        assertThat(respuesta.contenido()).hasSize(1);
        assertThat(respuesta.contenido().get(0).usuario()).isEqualTo("admin");
        assertThat(respuesta.contenido().get(0).operacion()).isEqualTo("CREACION");
        assertThat(respuesta.contenido().get(0).cambios()).containsExactly(
                new CampoCambiadoDTO("nombres", null, "Juana"));
    }

    // ---------- listarEliminadas ----------

    @Test
    void listarEliminadasUsaShapeCompletoConAuditoria() {
        Persona eliminada = personaDeEjemplo();
        eliminada.eliminarLogicamente();
        Direccion direccion = direccionDeEjemplo(eliminada);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Persona> pagina = new PageImpl<>(List.of(eliminada), pageable, 1);

        when(personaRepository.findByActivoFalse(pageable)).thenReturn(pagina);
        when(direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(eliminada)).thenReturn(Optional.of(direccion));
        when(personaMapper.toResponseDTO(eliminada, direccion)).thenReturn(mockRespuesta());

        var respuesta = personaService().listarEliminadas(pageable);

        assertThat(respuesta.contenido()).hasSize(1);
        verify(personaMapper).toResponseDTO(eliminada, direccion);
        verify(personaMapper, never()).toResumenDTO(any(), any());
    }

    // ---------- listar (busqueda avanzada) - validaciones y calculo de limites de edad ----------

    @Test
    void listarConEdadMinimaMayorQueEdadMaximaLanza400ConCampoEdadMaxima() {
        var filtro = new mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO(
                null, null, null, null, 40, 18, null, null, null, null, null, null);

        assertThatThrownBy(() -> personaService().listar(filtro, "ACTIVAS", PageRequest.of(0, 20)))
                .isInstanceOf(mx.personas.api.common.error.FormatoInvalidoException.class)
                .extracting(ex -> ((ApiException) ex).getDetalles().get(0).campo())
                .isEqualTo("edadMaxima");
    }

    @Test
    void listarConFechaRegistroDesdePosteriorAHastaLanza400ConCampoFechaRegistroHasta() {
        var filtro = new mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO(
                null, null, null, null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1), null, null, null, null);

        assertThatThrownBy(() -> personaService().listar(filtro, "ACTIVAS", PageRequest.of(0, 20)))
                .isInstanceOf(mx.personas.api.common.error.FormatoInvalidoException.class)
                .extracting(ex -> ((ApiException) ex).getDetalles().get(0).campo())
                .isEqualTo("fechaRegistroHasta");
    }

    @Test
    void listarConOrdenarPorDesconocidoLanza400ConCampoOrdenarPor() {
        var filtro = new mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO(
                null, null, null, null, null, null, null, null, null, null, "APELLIDO", null);

        assertThatThrownBy(() -> personaService().listar(filtro, "ACTIVAS", PageRequest.of(0, 20)))
                .isInstanceOf(mx.personas.api.common.error.FormatoInvalidoException.class)
                .extracting(ex -> ((ApiException) ex).getDetalles().get(0).campo())
                .isEqualTo("ordenarPor");
    }

    @Test
    void listarConDireccionOrdenDesconocidaLanza400ConCampoDireccionOrden() {
        var filtro = new mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO(
                null, null, null, null, null, null, null, null, null, null, "NOMBRE", "ARRIBA");

        assertThatThrownBy(() -> personaService().listar(filtro, "ACTIVAS", PageRequest.of(0, 20)))
                .isInstanceOf(mx.personas.api.common.error.FormatoInvalidoException.class)
                .extracting(ex -> ((ApiException) ex).getDetalles().get(0).campo())
                .isEqualTo("direccionOrden");
    }

    @Test
    void fechaNacimientoMaximaDesdeEdadMinimaIncluyeElCumpleanosExactoDeHoy() {
        LocalDate limite = ReflectionTestUtils.invokeMethod(
                personaService(), "fechaNacimientoMaximaDesdeEdadMinima", 18);

        assertThat(java.time.Period.between(limite, LocalDate.now()).getYears()).isEqualTo(18);
        assertThat(java.time.Period.between(limite.plusDays(1), LocalDate.now()).getYears()).isEqualTo(17);
    }

    @Test
    void fechaNacimientoMinimaDesdeEdadMaximaIncluyeElCumpleanosExactoDeHoy() {
        LocalDate limite = ReflectionTestUtils.invokeMethod(
                personaService(), "fechaNacimientoMinimaDesdeEdadMaxima", 65);

        assertThat(java.time.Period.between(limite, LocalDate.now()).getYears()).isEqualTo(65);
        assertThat(java.time.Period.between(limite.minusDays(1), LocalDate.now()).getYears()).isEqualTo(66);
    }

    // ---------- aplicarOrden (FR-010, FR-011) - logica pura, sin BD ----------

    private Pageable invocarAplicarOrden(String ordenarPor, String direccionOrden, Pageable pageable)
            throws Exception {
        var metodo = mx.personas.api.persona.service.PersonaService.class.getDeclaredMethod(
                "aplicarOrden", String.class, String.class, Pageable.class);
        metodo.setAccessible(true);
        return (Pageable) metodo.invoke(personaService(), ordenarPor, direccionOrden, pageable);
    }

    @Test
    void aplicarOrdenSinOrdenarPorDevuelveElPageableSinModificar() throws Exception {
        Pageable original = PageRequest.of(0, 20);

        Pageable resultado = invocarAplicarOrden(null, null, original);

        assertThat(resultado).isSameAs(original);
        assertThat(resultado.getSort().isUnsorted()).isTrue();
    }

    @Test
    void aplicarOrdenPorNombreOrdenaPorApellidosYNombres() throws Exception {
        Pageable resultado = invocarAplicarOrden("NOMBRE", "DESC", PageRequest.of(0, 20));

        List<String> propiedades = resultado.getSort().stream()
                .map(org.springframework.data.domain.Sort.Order::getProperty).toList();
        assertThat(propiedades).containsExactly("apellidos", "nombres");
        assertThat(resultado.getSort().stream().findFirst().orElseThrow().getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    void aplicarOrdenPorFechaNacimientoYFechaRegistroMapeaLaPropiedadCorrecta() throws Exception {
        Pageable porNacimiento = invocarAplicarOrden("FECHA_NACIMIENTO", "ASC", PageRequest.of(0, 20));
        Pageable porRegistro = invocarAplicarOrden("FECHA_REGISTRO", "ASC", PageRequest.of(0, 20));

        assertThat(porNacimiento.getSort().stream().findFirst().orElseThrow().getProperty())
                .isEqualTo("fechaNacimiento");
        assertThat(porRegistro.getSort().stream().findFirst().orElseThrow().getProperty())
                .isEqualTo("createdAt");
    }

    @Test
    void aplicarOrdenSinDireccionOrdenUsaAscendentePorDefecto() throws Exception {
        Pageable resultado = invocarAplicarOrden("NOMBRE", null, PageRequest.of(0, 20));

        assertThat(resultado.getSort().stream().findFirst().orElseThrow().getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    private PersonaResponseDTO mockRespuesta() {
        return new PersonaResponseDTO(UUID.randomUUID(), "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678",
                null, null, null, null, null);
    }
}
