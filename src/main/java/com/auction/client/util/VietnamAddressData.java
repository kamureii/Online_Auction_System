package com.auction.client.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class VietnamAddressData {
    private static final String RESOURCE_PATH = "/data/vietnam-addresses-v1.json";
    private static final List<Province> PROVINCES = loadProvinces();

    private VietnamAddressData() {}

    public static List<String> provinceNames() {
        return PROVINCES.stream().map(Province::name).toList();
    }

    public static List<String> districtNames(String provinceName) {
        return findProvince(provinceName)
                .map(province -> safeList(province.districts).stream().map(District::name).toList())
                .orElseGet(Collections::emptyList);
    }

    public static List<String> wardNames(String provinceName, String districtName) {
        return findProvince(provinceName)
                .flatMap(province -> safeList(province.districts).stream()
                        .filter(district -> sameName(district.name, districtName))
                        .findFirst())
                .map(district -> safeList(district.wards).stream().map(Ward::name).toList())
                .orElseGet(Collections::emptyList);
    }

    private static Optional<Province> findProvince(String provinceName) {
        return PROVINCES.stream()
                .filter(province -> sameName(province.name, provinceName))
                .findFirst();
    }

    private static boolean sameName(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private static List<Province> loadProvinces() {
        try (InputStream stream = VietnamAddressData.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                return List.of();
            }
            Type listType = new TypeToken<List<Province>>() {}.getType();
            List<Province> provinces = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    listType
            );
            return provinces == null ? List.of() : provinces;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class Province {
        private String name;
        private List<District> districts = new ArrayList<>();

        private String name() {
            return name == null ? "" : name;
        }
    }

    private static final class District {
        private String name;
        private List<Ward> wards = new ArrayList<>();

        private String name() {
            return name == null ? "" : name;
        }
    }

    private static final class Ward {
        private String name;

        private String name() {
            return name == null ? "" : name;
        }
    }
}
