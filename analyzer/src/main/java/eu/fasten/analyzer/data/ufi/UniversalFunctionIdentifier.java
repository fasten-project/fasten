package eu.fasten.analyzer.data.ufi;

import java.util.Map;

public interface UniversalFunctionIdentifier<T> {
    UFI convertToUFI(T item);

    Map<UFI, T> mappings();
}
