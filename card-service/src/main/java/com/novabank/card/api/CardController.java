package com.novabank.card.api;

import com.novabank.card.api.dto.BlockCardRequest;
import com.novabank.card.api.dto.CardResponse;
import com.novabank.card.api.dto.CreateCardRequest;
import com.novabank.card.domain.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    @Operation(summary = "List a customer's cards")
    @ApiResponse(responseCode = "200", description = "Cards for the customer")
    public List<CardResponse> listCards(@PathVariable UUID customerId) {
        return cardService.listForCustomer(customerId).stream()
                .map(CardResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Issue a new card", description = "A masked PAN is fabricated server-side; the full PAN is never accepted or returned.")
    @ApiResponse(responseCode = "201", description = "Card issued")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<CardResponse> issueCard(
            @PathVariable UUID customerId, @Valid @RequestBody CreateCardRequest request) {
        CardResponse response = CardResponse.from(cardService.create(customerId, request));
        return ResponseEntity.created(URI.create(
                "/api/v1/customers/" + customerId + "/cards/" + response.id())).body(response);
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Get card detail (masked PAN only)")
    @ApiResponse(responseCode = "200", description = "Card found")
    @ApiResponse(responseCode = "404", description = "Card not found for this customer")
    public CardResponse getCard(@PathVariable UUID customerId, @PathVariable UUID cardId) {
        return CardResponse.from(cardService.getOwnedByCustomer(customerId, cardId));
    }

    @PostMapping("/{cardId}/block")
    @Operation(summary = "Block a card", description = "Requires a reason. Blocking an already-blocked card fails.")
    @ApiResponse(responseCode = "200", description = "Card blocked")
    @ApiResponse(responseCode = "404", description = "Card not found for this customer")
    @ApiResponse(responseCode = "422", description = "Card is already blocked")
    public CardResponse block(@PathVariable UUID customerId, @PathVariable UUID cardId,
                              @Valid @RequestBody BlockCardRequest request) {
        return CardResponse.from(cardService.block(customerId, cardId, request.reason()));
    }

    @PostMapping("/{cardId}/unblock")
    @Operation(summary = "Unblock a card", description = "Unblocking a card that isn't blocked fails.")
    @ApiResponse(responseCode = "200", description = "Card unblocked")
    @ApiResponse(responseCode = "404", description = "Card not found for this customer")
    @ApiResponse(responseCode = "422", description = "Card is not blocked")
    public CardResponse unblock(@PathVariable UUID customerId, @PathVariable UUID cardId) {
        return CardResponse.from(cardService.unblock(customerId, cardId));
    }
}
