package com.argus.evidence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    List<UploadedFile> findTop100ByOrderByTsDesc();

    Optional<UploadedFile> findByStoredName(String storedName);
}
