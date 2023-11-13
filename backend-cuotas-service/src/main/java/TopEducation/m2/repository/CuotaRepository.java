package TopEducation.m2.repository;

import TopEducation.m2.entity.CuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public interface CuotaRepository extends JpaRepository<CuotaEntity, Long> {
    /*Buscar Cuotas por id de estudiante*/
    @Query("SELECT cuota FROM CuotaEntity cuota WHERE cuota.id_estudiante = :idEstudiante")
    ArrayList<CuotaEntity> findAllByEstudianteId(@Param("idEstudiante") Long idEstudiante);

    /*Buscar Cuota por ID de cuota*/
    @Query("SELECT cuota FROM CuotaEntity cuota WHERE cuota.id_cuota = :idCuota")
    CuotaEntity findByIdCuota(Long idCuota);
}
