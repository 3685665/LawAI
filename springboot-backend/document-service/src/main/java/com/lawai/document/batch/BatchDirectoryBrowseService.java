package com.lawai.document.batch;

import com.lawai.common.model.AuthenticatedUser;
import com.lawai.document.batch.dto.DirectoryBrowseEntryDto;
import com.lawai.document.batch.dto.DirectoryBrowseResponse;
import com.lawai.document.service.DocumentProcessingProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class BatchDirectoryBrowseService {

  private final DocumentProcessingProperties properties;

  public BatchDirectoryBrowseService(DocumentProcessingProperties properties) {
    this.properties = properties;
  }

  public DirectoryBrowseResponse browse(AuthenticatedUser user, String requestedPath) {
    requireAdmin(user);
    List<Path> roots = resolveRoots();
    List<String> rootPaths = roots.stream().map(path -> path.toString()).toList();

    if (!StringUtils.hasText(requestedPath)) {
      List<DirectoryBrowseEntryDto> entries = roots.stream()
          .map(path -> new DirectoryBrowseEntryDto(path.getFileName() == null ? path.toString() : path.getFileName().toString(), path.toString(), true))
          .toList();
      return new DirectoryBrowseResponse(null, null, rootPaths, entries);
    }

    Path current = Path.of(requestedPath.trim()).toAbsolutePath().normalize();
    ensureWithinRoots(current, roots);

    if (!Files.exists(current) || !Files.isDirectory(current)) {
      throw new IllegalArgumentException("Dizin bulunamadi: " + current);
    }

    String parentPath = findParentWithinRoots(current, roots);
    List<DirectoryBrowseEntryDto> entries = listChildDirectories(current);
    return new DirectoryBrowseResponse(current.toString(), parentPath, rootPaths, entries);
  }

  private List<DirectoryBrowseEntryDto> listChildDirectories(Path current) {
    try (Stream<Path> stream = Files.list(current)) {
      return stream
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
          .map(path -> new DirectoryBrowseEntryDto(path.getFileName().toString(), path.toString(), true))
          .toList();
    } catch (Exception exception) {
      throw new IllegalStateException("Dizin icerigi okunamadi: " + exception.getMessage(), exception);
    }
  }

  private String findParentWithinRoots(Path current, List<Path> roots) {
    Path parent = current.getParent();
    if (parent == null) {
      return null;
    }
    Path normalizedParent = parent.toAbsolutePath().normalize();
    if (isRootPath(normalizedParent, roots)) {
      return null;
    }
    for (Path root : roots) {
      if (normalizedParent.startsWith(root)) {
        return normalizedParent.toString();
      }
    }
    return null;
  }

  private void ensureWithinRoots(Path path, List<Path> roots) {
    if (isRootPath(path, roots)) {
      return;
    }
    boolean allowed = roots.stream().anyMatch(path::startsWith);
    if (!allowed) {
      throw new IllegalArgumentException("Bu dizine erisim izni yok.");
    }
  }

  private boolean isRootPath(Path path, List<Path> roots) {
    return roots.stream().anyMatch(root -> root.equals(path));
  }

  private List<Path> resolveRoots() {
    Set<Path> roots = new LinkedHashSet<>();
    addRootIfExists(roots, Path.of(properties.uploadDir()).toAbsolutePath().normalize());
    addRootIfExists(roots, Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize());
    addRootIfExists(roots, Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
    for (File fileRoot : File.listRoots()) {
      addRootIfExists(roots, fileRoot.toPath().toAbsolutePath().normalize());
    }
    if (roots.isEmpty()) {
      throw new IllegalStateException("Gecerli bir dizin koku bulunamadi.");
    }
    return new ArrayList<>(roots);
  }

  private void addRootIfExists(Set<Path> roots, Path path) {
    if (Files.isDirectory(path)) {
      roots.add(path);
    }
  }

  private void requireAdmin(AuthenticatedUser user) {
    if (user == null || !user.isAdmin()) {
      throw new IllegalArgumentException("Bu islem icin yonetici yetkisi gerekli.");
    }
  }
}
