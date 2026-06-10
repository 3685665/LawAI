package com.lawai.document.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentEmbeddingService {

  private final int dimensions;

  public DocumentEmbeddingService(DocumentProcessingProperties properties) {
    this.dimensions = properties.embeddingDimensions();
  }

  public String embedLiteral(String text) {
    double[] vector = new double[dimensions];
    for (String token : tokenize(text)) {
      byte[] digest = sha256(token);
      int index = Math.floorMod(toInt(digest), dimensions);
      double sign = digest[4] % 2 == 0 ? 1.0 : -1.0;
      vector[index] += sign;
    }
    normalize(vector);
    return toPgVectorLiteral(vector);
  }

  private List<String> tokenize(String text) {
    String normalized = Normalizer.normalize(text == null ? "" : text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ");
    List<String> tokens = new ArrayList<>();
    for (String token : normalized.split("\\s+")) {
      if (token.length() > 2) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  private byte[] sha256(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 desteklenmiyor.", exception);
    }
  }

  private int toInt(byte[] digest) {
    return ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16) | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
  }

  private void normalize(double[] vector) {
    double sum = 0.0;
    for (double value : vector) {
      sum += value * value;
    }
    double norm = Math.sqrt(sum);
    if (norm == 0.0) {
      return;
    }
    for (int index = 0; index < vector.length; index++) {
      vector[index] = vector[index] / norm;
    }
  }

  private String toPgVectorLiteral(double[] vector) {
    StringBuilder builder = new StringBuilder("[");
    for (int index = 0; index < vector.length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append(String.format(Locale.ROOT, "%.8f", vector[index]));
    }
    return builder.append(']').toString();
  }
}
