package com.mydelivery.admin.modulos.pagamentos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Resposta enxuta do MP Cloud API. Ignora qualquer campo extra — o MP retorna
 * dezenas que não nos interessam.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpPaymentResponse {

    private Long id;

    /** approved | pending | rejected | cancelled | refunded | charged_back | in_process | etc. */
    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    /** "pix" / "bolbradesco" / "credit_card" / etc. */
    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("transaction_amount")
    private java.math.BigDecimal transactionAmount;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("date_approved")
    private String dateApproved;

    @JsonProperty("point_of_interaction")
    private PointOfInteraction pointOfInteraction;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PointOfInteraction {
        @JsonProperty("transaction_data")
        private TransactionData transactionData;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionData {
        @JsonProperty("qr_code")
        private String qrCode;

        @JsonProperty("qr_code_base64")
        private String qrCodeBase64;

        @JsonProperty("ticket_url")
        private String ticketUrl;
    }
}
