package com.spectrumai.backend.export.bigquery.repository;

import com.spectrumai.backend.company.model.Company;
import com.spectrumai.backend.export.bigquery.model.BigQuerySync;
import com.spectrumai.backend.search.model.Search;
import com.spectrumai.backend.search.model.SearchStatus;
import com.spectrumai.backend.user.model.Role;
import com.spectrumai.backend.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comportamento do upsert de {@link BigQuerySync} contra o Postgres de verdade.
 *
 * <p>O que está sob teste é a unicidade {@code (tenant_id, search_id)}: nenhum mock
 * reproduz uma violação de constraint, e é ela que decide se duas cargas simultâneas
 * da mesma pesquisa convivem ou derrubam a requisição. De passagem, valida a
 * amarração dos parâmetros {@code uuid} e {@code timestamptz} da query nativa.
 *
 * <p>Usa o banco de {@code DATABASE_URL}, como {@code SpectrumAiApplicationTests}.
 * {@code @Transactional} no teste desfaz tudo no fim.
 */
@SpringBootTest
@Transactional
class BigQuerySyncRepositoryTest {

    @Autowired
    private BigQuerySyncRepository repository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID searchId;

    @BeforeEach
    void setUp() {
        Company tenant = Company.builder()
                .id(UUID.randomUUID())
                .name("Montadora de Teste")
                .active(true)
                .build();
        entityManager.persist(tenant);

        User owner = User.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .fullName("Analista de Teste")
                .email("analista+" + UUID.randomUUID() + "@spectrum.ai")
                .passwordHash("nao-e-uma-senha")
                .role(Role.ANALYST)
                .active(true)
                .build();
        entityManager.persist(owner);

        Search search = Search.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .requestedBy(owner)
                .brand("Toyota")
                .model("Corolla")
                .categories(new String[]{"Rodas"})
                .status(SearchStatus.COMPLETED)
                .completedAt(OffsetDateTime.now())
                .build();
        entityManager.persist(search);

        // As linhas precisam existir no banco antes do upsert: ele é SQL nativo e não
        // dispara o flush das entidades pendentes, então as FKs falhariam.
        entityManager.flush();

        tenantId = tenant.getId();
        searchId = search.getId();
    }

    @Test
    @DisplayName("insere o registro quando a pesquisa ainda não tem carga")
    void insertsWhenAbsent() {
        repository.upsert(UUID.randomUUID(), tenantId, searchId, "hash-inicial", 254,
                OffsetDateTime.now());

        Optional<BigQuerySync> saved = repository.findByTenantIdAndSearchId(tenantId, searchId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getContentHash()).isEqualTo("hash-inicial");
        assertThat(saved.get().getRowCount()).isEqualTo(254);
        assertThat(saved.get().getIngestedAt()).isNotNull();
    }

    /**
     * A corrida real: o gancho automático e o endpoint manual leem "não existe
     * registro" ao mesmo tempo e os dois tentam inserir, com ids diferentes. A
     * segunda escrita tem de atualizar, não estourar — as linhas já foram para o
     * BigQuery e a requisição falharia sem haver nada errado.
     */
    @Test
    @DisplayName("segunda carga concorrente atualiza em vez de violar a unicidade")
    void resolvesConflictInsteadOfFailing() {
        UUID idDoAutoSync = UUID.randomUUID();
        UUID idDoEndpoint = UUID.randomUUID();
        repository.upsert(idDoAutoSync, tenantId, searchId, "hash-do-auto-sync", 254,
                OffsetDateTime.now());

        assertThatCode(() -> repository.upsert(idDoEndpoint, tenantId, searchId,
                "hash-do-endpoint", 260, OffsetDateTime.now()))
                .doesNotThrowAnyException();

        BigQuerySync saved = repository.findByTenantIdAndSearchId(tenantId, searchId).orElseThrow();
        // Vence a última escrita, e a linha original é preservada — id inalterado.
        assertThat(saved.getId()).isEqualTo(idDoAutoSync);
        assertThat(saved.getContentHash()).isEqualTo("hash-do-endpoint");
        assertThat(saved.getRowCount()).isEqualTo(260);
    }

    @Test
    @DisplayName("recarga da mesma pesquisa atualiza o hash e o carimbo")
    void updatesOnReingest() {
        UUID id = UUID.randomUUID();
        OffsetDateTime primeira = OffsetDateTime.now().minusDays(1);
        repository.upsert(id, tenantId, searchId, "hash-antigo", 254, primeira);

        repository.upsert(id, tenantId, searchId, "hash-novo", 251, OffsetDateTime.now());

        BigQuerySync saved = repository.findByTenantIdAndSearchId(tenantId, searchId).orElseThrow();
        assertThat(saved.getContentHash()).isEqualTo("hash-novo");
        assertThat(saved.getRowCount()).isEqualTo(251);
        assertThat(saved.getIngestedAt()).isAfter(primeira);
    }
}
