package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.ambiance.YoutubePicker;
import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.service.YouTubeDownloader;
import com.marianocarrizo.dynamusic.service.YtDlpManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.Optional;

public final class YouTubeBarController {

    interface Host {
        String selectedPlaylistName();

        void showPendingPlaylistEntry(String text);

        void addPlaylistItem(String playlistName, PlaylistItem item);

        void refreshPlaylistView();

        void playNow(File file, String title);
    }


    private static final String INVALID_URL_MESSAGE = "Not a valid YouTube URL.";

    @FXML private TextField txtYoutubeUrl;
    @FXML private Button btnAddYoutube;
    @FXML private Button btnPlayYoutube;
    @FXML private Button btnBrowseYoutube;
    @FXML private Label lblYoutubeError;

    private final YouTubeDownloader youTubeDownloader = new YouTubeDownloader();
    private final PauseTransition statusClear = new PauseTransition(Duration.seconds(5));
    private Host host;

    void attach(Host host) {
        this.host = host;
        statusClear.setOnFinished(e -> hideMessage());
        YtDlpManager manager = YtDlpManager.get();
        manager.setStatusListener(msg -> Platform.runLater(() -> showStatus(msg)));
        manager.ensureReadyAsync();
    }

    void focusUrl() {
        txtYoutubeUrl.requestFocus();
    }

    @FXML
    private void onAddYoutubeUrl() {
        hideMessage();

        String playlistName = host.selectedPlaylistName();
        if (playlistName == null || playlistName.isBlank()) {
            showError("Select a playlist first.");
            return;
        }

        String url = txtYoutubeUrl.getText().trim();
        Optional<String> videoId = YouTubeDownloader.extractVideoId(url);
        if (videoId.isEmpty()) {
            showError(INVALID_URL_MESSAGE);
            return;
        }

        btnAddYoutube.setDisable(true);
        host.showPendingPlaylistEntry("⬇ Downloading... (" + videoId.get() + ")");
        txtYoutubeUrl.clear();

        youTubeDownloader.downloadAsync(url, videoId.get(),
                result -> Platform.runLater(() -> {
                    btnAddYoutube.setDisable(false);
                    host.addPlaylistItem(playlistName, new PlaylistItem(result.getPath(), url, result.getTitle()));
                }),
                err -> Platform.runLater(() -> {
                    btnAddYoutube.setDisable(false);
                    host.refreshPlaylistView();
                    new Alert(Alert.AlertType.ERROR, "Download failed: " + err).showAndWait();
                })
        );
    }

    @FXML
    private void onPlayYoutubeUrl() {
        hideMessage();
        playNow(txtYoutubeUrl.getText().trim());
    }

    @FXML
    private void onBrowseYouTube() {
        Stage owner = (Stage) btnBrowseYoutube.getScene().getWindow();
        YoutubePicker.show(owner, youTubeDownloader, result -> playNow(result.url()));
    }

    private void playNow(String url) {
        Optional<String> videoId = YouTubeDownloader.extractVideoId(url);
        if (videoId.isEmpty()) {
            showError(INVALID_URL_MESSAGE);
            return;
        }
        btnPlayYoutube.setDisable(true);
        showStatus("Downloading track...");
        youTubeDownloader.downloadAsync(url, videoId.get(),
                result -> Platform.runLater(() -> {
                    btnPlayYoutube.setDisable(false);
                    statusClear.playFromStart();
                    txtYoutubeUrl.clear();
                    host.playNow(new File(result.getPath()), result.displayName());
                }),
                err -> Platform.runLater(() -> {
                    btnPlayYoutube.setDisable(false);
                    showError("Download failed: " + err);
                })
        );
    }

    private void showStatus(String message) {
        boolean error = message.startsWith("Could not");
        lblYoutubeError.setText(message);
        lblYoutubeError.setStyle(error ? "" : "-fx-text-fill: #8a8f98;");
        setMessageVisible(true);
        statusClear.stop();
        if (!message.endsWith("...")) {
            statusClear.setDuration(Duration.seconds(error ? 10 : 4));
            statusClear.playFromStart();
        }
    }

    private void showError(String message) {
        statusClear.stop();
        lblYoutubeError.setStyle("");
        lblYoutubeError.setText(message);
        setMessageVisible(true);
    }

    private void hideMessage() {
        lblYoutubeError.setText("");
        setMessageVisible(false);
    }

    private void setMessageVisible(boolean visible) {
        lblYoutubeError.setVisible(visible);
        lblYoutubeError.setManaged(visible);
    }
}
