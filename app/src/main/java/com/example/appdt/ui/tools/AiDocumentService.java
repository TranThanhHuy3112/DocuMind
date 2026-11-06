package com.example.appdt.ui.tools;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.appdt.model.DocumentModel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads local sources only after a user presses an AI action, then sends them to AI Logic. */
final class AiDocumentService {
    // Inline data is base64 encoded; keep well below the 20 MB total request limit.
    private static final int MAX_SOURCE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 12 * 1024 * 1024;
    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private ListenableFuture<GenerateContentResponse> pending;

    interface Callback {
        void onSuccess(String answer);
        void onError(String message);
    }

    AiDocumentService(Context context) { this.context = context.getApplicationContext(); }

    void ask(List<DocumentModel> documents, String instruction, Callback callback) {
        io.execute(() -> {
            try {
                Content.Builder prompt = new Content.Builder();
                int totalBytes = 0;
                for (int i = 0; i < documents.size(); i++) {
                    if (closed || Thread.currentThread().isInterrupted()) return;
                    DocumentModel source = documents.get(i);
                    byte[] bytes = read(source);
                    String extension = source.getExtension().toLowerCase(Locale.ROOT);
                    if (extension.equals("docx")) {
                        bytes = OfficeTextExtractor.docx(bytes).getBytes(StandardCharsets.UTF_8);
                    } else if (extension.equals("xlsx")) {
                        bytes = OfficeTextExtractor.xlsxCsv(bytes).getBytes(StandardCharsets.UTF_8);
                    }
                    totalBytes += bytes.length;
                    if (totalBytes > MAX_REQUEST_BYTES) {
                        throw new IOException("Tổng dung lượng nguồn vượt 12 MB. Hãy chọn tệp nhỏ hơn.");
                    }
                    String mime = mime(source);
                    prompt.addText("Nguồn " + (i + 1) + ": " + source.getName() + "\n");
                    prompt.addInlineData(bytes, mime);
                }
                prompt.addText("\nTrả lời bằng tiếng Việt. Chỉ dùng nội dung từ các nguồn đính kèm; "
                        + "nêu rõ khi tài liệu không có thông tin. Không tự tạo số liệu hay trích dẫn. "
                        + "Ghi tên nguồn khi nêu kết luận. Yêu cầu: " + instruction);
                if (closed || Thread.currentThread().isInterrupted()) return;
                GenerativeModel model = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                        .generativeModel("gemini-3.5-flash-lite");
                ListenableFuture<GenerateContentResponse> future =
                        GenerativeModelFutures.from(model).generateContent(prompt.build());
                pending = future;
                Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                    @Override public void onSuccess(GenerateContentResponse result) {
                        if (closed) return;
                        String answer = result.getText();
                        if (answer == null || answer.trim().isEmpty()) {
                            callback.onError("Dịch vụ chưa trả về nội dung. Hãy thử lại.");
                        } else callback.onSuccess(answer.trim());
                    }
                    @Override public void onFailure(Throwable error) {
                        if (!closed) callback.onError(messageFor(error));
                    }
                }, runnable -> main.post(runnable));
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                main.post(() -> { if (!closed) callback.onError(error.getMessage()); });
            } catch (RuntimeException error) {
                main.post(() -> { if (!closed) callback.onError("Không thể khởi tạo công cụ AI. Hãy kiểm tra cấu hình dự án."); });
            }
        });
    }

    private byte[] read(DocumentModel source) throws IOException {
        if (source.getSize() > MAX_SOURCE_BYTES) {
            throw new IOException("Tệp " + source.getName() + " vượt quá 10 MB. Hãy chọn tệp nhỏ hơn.");
        }
        try (InputStream stream = context.getContentResolver()
                .openInputStream(Uri.parse(source.getPath()))) {
            if (stream == null) throw new IOException("Không thể mở " + source.getName() + ". Hãy thêm lại tệp.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > MAX_SOURCE_BYTES) {
                    throw new IOException("Tệp " + source.getName() + " vượt quá 10 MB. Hãy chọn tệp nhỏ hơn.");
                }
                output.write(buffer, 0, count);
            }
            if (output.size() == 0) throw new IOException("Tệp " + source.getName() + " không có nội dung.");
            return output.toByteArray();
        }
    }

    private static String mime(DocumentModel source) throws IOException {
        String extension = source.getExtension().toLowerCase(Locale.ROOT);
        if (extension.equals("pdf")) return "application/pdf";
        if (extension.equals("txt") || extension.equals("csv")
                || extension.equals("docx") || extension.equals("xlsx")) return "text/plain";
        if (extension.equals("mp3")) return "audio/mpeg";
        if (extension.equals("wav")) return "audio/wav";
        if (extension.equals("m4a")) return "audio/mp4";
        if (extension.equals("mp4")) return "video/mp4";
        if (extension.equals("mov")) return "video/quicktime";
        throw new IOException("Định dạng " + extension.toUpperCase(Locale.ROOT)
                + " chưa thể gửi tới AI. Hãy xuất thành PDF, DOCX, XLSX, TXT, CSV, MP4, MP3, WAV hoặc M4A.");
    }

    private static String messageFor(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String detail = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
        if (detail.contains("high demand") || detail.contains("overloaded"))
            return "Dịch vụ AI đang bận. Hãy thử lại sau ít phút.";
        if (detail.contains("quota") || detail.contains("429") || detail.contains("resource exhausted"))
            return "Đã chạm giới hạn AI miễn phí. Hãy thử lại sau.";
        return "Không thể xử lý lúc này. Hãy kiểm tra mạng và thử lại.";
    }

    void close() {
        closed = true;
        if (pending != null) pending.cancel(true);
        io.shutdownNow();
    }
}
