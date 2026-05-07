package com.auction.shared.model;

/**
 * Tác phẩm nghệ thuật (kế thừa Item).
 * Ví dụ: tranh, tượng, đồ cổ...
 */
public class Art extends Item {
    private String artist;
    private String medium;

    public Art() {
        this.category = "ART";
    }

    public Art(int id, String name, String description,
               double startingPrice, double currentPrice, double minIncrement, int sellerId) {
        super(id, name, description, "ART", startingPrice, currentPrice, minIncrement, sellerId);
    }

    public Art(String name, String description,
               double startingPrice, double minIncrement, int sellerId) {
        super(name, description, "ART", startingPrice, minIncrement, sellerId);
    }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }

    @Override
    public String getCategorySpecificInfo() {
        return "🎨 Nghệ thuật" + (artist != null ? " - Nghệ sĩ: " + artist : "");
    }
}
