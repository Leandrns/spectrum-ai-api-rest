package com.spectrumai.backend.vehicles.fipe.client;

import com.spectrumai.backend.config.AppProperties;
import com.spectrumai.backend.vehicles.fipe.dto.FipeBrand;
import com.spectrumai.backend.vehicles.fipe.dto.FipeModel;
import com.spectrumai.backend.vehicles.fipe.dto.FipeYear;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Cliente HTTP da API FIPE (https://fipe.online/docs/api/fipe). Usado
 * exclusivamente pelo job de importação do catálogo de veículos.
 */
@Slf4j
@Component
public class FipeApiClient {

    private static final ParameterizedTypeReference<List<FipeBrand>> BRAND_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FipeModel>> MODEL_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FipeYear>> YEAR_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String vehicleType;
    private final boolean configured;

    public FipeApiClient(AppProperties properties) {
        AppProperties.Fipe fipe = properties.fipe();
        this.vehicleType = fipe.vehicleType();
        this.configured = fipe.apiToken() != null && !fipe.apiToken().isBlank();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(fipe.timeoutSeconds());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(fipe.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        if (this.configured) {
            builder.defaultHeader("X-Subscription-Token", fipe.apiToken());
        }
        this.restClient = builder.build();
    }

    public boolean isConfigured() {
        return configured;
    }

    public List<FipeBrand> listBrands() {
        return getList("/" + vehicleType + "/brands", BRAND_LIST);
    }

    public List<FipeModel> listModels(String brandCode) {
        return getList("/" + vehicleType + "/brands/" + brandCode + "/models", MODEL_LIST);
    }

    public List<FipeYear> listYears(String brandCode, String modelCode) {
        return getList(
                "/" + vehicleType + "/brands/" + brandCode + "/models/" + modelCode + "/years",
                YEAR_LIST);
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> body = restClient.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new FipeApiException(
                                "FIPE retornou erro " + res.getStatusCode() + " em " + path);
                    })
                    .body(type);
            return body == null ? Collections.emptyList() : body;
        } catch (FipeApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new FipeApiException(
                    "Erro HTTP ao consultar FIPE (" + path + "): " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            throw new FipeApiException("Falha de comunicação com a FIPE (" + path + ")", e);
        }
    }
}
