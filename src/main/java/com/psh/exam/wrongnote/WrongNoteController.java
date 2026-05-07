package com.psh.exam.wrongnote;

import com.psh.exam.security.AccountPrincipal;
import com.psh.exam.wrongnote.WrongNoteDtos.CreateWrongNoteRequest;
import com.psh.exam.wrongnote.WrongNoteDtos.UpdateWrongNoteRequest;
import com.psh.exam.wrongnote.WrongNoteDtos.WrongNoteResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wrong-notes")
public class WrongNoteController {

    private final WrongNoteService wrongNoteService;

    public WrongNoteController(WrongNoteService wrongNoteService) {
        this.wrongNoteService = wrongNoteService;
    }

    @GetMapping
    public List<WrongNoteResponse> listWrongNotes(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) Boolean resolved
    ) {
        return wrongNoteService.listWrongNotes(principal.accountId(), resolved);
    }

    @PostMapping
    public WrongNoteResponse createWrongNote(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody CreateWrongNoteRequest request
    ) {
        return wrongNoteService.createWrongNote(principal.accountId(), request);
    }

    @PatchMapping("/{noteId}")
    public WrongNoteResponse updateWrongNote(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Long noteId,
            @RequestBody UpdateWrongNoteRequest request
    ) {
        return wrongNoteService.updateWrongNote(principal.accountId(), noteId, request);
    }
}
