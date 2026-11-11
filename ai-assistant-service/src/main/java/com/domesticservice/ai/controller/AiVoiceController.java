package com.domesticservice.ai.controller;

import com.domesticservice.ai.service.AiTranscriptionService;
import com.domesticservice.ai.service.AiVoiceRefinementService;
import com.domesticservice.common.web.ServiceHttpClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai-assistant/voice")
class AiVoiceController {
    private final AiTranscriptionService transcription;
    private final AiVoiceRefinementService refinement;
    private final ServiceHttpClient identity;

    AiVoiceController(AiTranscriptionService transcription, AiVoiceRefinementService refinement,
                      @Value("${clients.identity.base-url}") String identityUrl) {
        this.transcription = transcription;
        this.refinement = refinement;
        identity = new ServiceHttpClient(identityUrl);
    }

    record Transcript(String text) {}
    record Refinement(@NotBlank @Size(max = 4000) String text) {}

    @PostMapping("/refinements")
    AiVoiceRefinementService.Result refine(@RequestHeader(value = "Authorization", required = false) String token,
                                          @Valid @RequestBody Refinement request) {
        identity.authenticate(token, "CUSTOMER");
        return refinement.refine(request.text());
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Transcript transcribe(@RequestHeader(value = "Authorization", required = false) String token,
                          @RequestPart("audio") MultipartFile audio) {
        identity.authenticate(token, "CUSTOMER");
        return new Transcript(transcription.transcribe(audio));
    }
}
