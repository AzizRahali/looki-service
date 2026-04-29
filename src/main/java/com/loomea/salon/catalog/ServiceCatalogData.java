package com.loomea.salon.catalog;

import java.util.List;

/**
 * Hardcoded two-level service catalog.
 * Level 1 = category (Hair, Nails, …)
 * Level 2 = concrete service offered within that category.
 *
 * Icons are Material icon names used by the Flutter frontend.
 */
public final class ServiceCatalogData {

    private ServiceCatalogData() {}

    public record Level2(String name, String icon) {}

    public record Level1(String category, String icon, List<Level2> services) {}

    public static final List<Level1> CATALOG = List.of(

        new Level1("Hair", "content_cut", List.of(
            new Level2("Haircut (Men's)",       "content_cut"),
            new Level2("Haircut (Women's)",     "content_cut"),
            new Level2("Blowout / Blowdry",     "air"),
            new Level2("Hair Coloring",         "palette"),
            new Level2("Highlights",            "format_paint"),
            new Level2("Balayage",              "format_paint"),
            new Level2("Keratin Treatment",     "spa"),
            new Level2("Hair Treatment / Mask", "spa"),
            new Level2("Hair Extensions",       "waves"),
            new Level2("Perm",                  "loop")
        )),

        new Level1("Nails", "brush", List.of(
            new Level2("Manicure",        "brush"),
            new Level2("Pedicure",        "brush"),
            new Level2("Gel Manicure",    "auto_awesome"),
            new Level2("Gel Pedicure",    "auto_awesome"),
            new Level2("Acrylic Nails",   "star"),
            new Level2("Nail Art",        "palette"),
            new Level2("Nail Removal",    "remove_circle_outline")
        )),

        new Level1("Skin & Face", "face_retouching_natural", List.of(
            new Level2("Facial (Basic)",        "face"),
            new Level2("Deep Cleansing Facial", "face"),
            new Level2("Anti-Aging Facial",     "face"),
            new Level2("Eyebrow Shaping",       "face"),
            new Level2("Eyebrow Threading",     "face"),
            new Level2("Eyebrow Tint",          "face"),
            new Level2("Microdermabrasion",     "face_retouching_natural"),
            new Level2("Chemical Peel",         "science")
        )),

        new Level1("Lashes", "visibility", List.of(
            new Level2("Lash Extensions (Classic)", "visibility"),
            new Level2("Lash Extensions (Volume)",  "visibility"),
            new Level2("Lash Lift",                 "arrow_upward"),
            new Level2("Lash Tint",                 "color_lens")
        )),

        new Level1("Waxing", "spa", List.of(
            new Level2("Full Leg Wax",   "straighten"),
            new Level2("Half Leg Wax",   "straighten"),
            new Level2("Arm Wax",        "straighten"),
            new Level2("Underarm Wax",   "self_improvement"),
            new Level2("Bikini Wax",     "spa"),
            new Level2("Brazilian Wax",  "spa"),
            new Level2("Back Wax",       "accessibility"),
            new Level2("Eyebrow Wax",    "face"),
            new Level2("Full Face Wax",  "face")
        )),

        new Level1("Makeup", "brush", List.of(
            new Level2("Full Makeup",    "brush"),
            new Level2("Bridal Makeup",  "favorite"),
            new Level2("Evening Makeup", "nights_stay"),
            new Level2("Natural Makeup", "eco")
        )),

        new Level1("Beard & Shave", "face", List.of(
            new Level2("Shave (Classic)", "face"),
            new Level2("Beard Trim",      "content_cut"),
            new Level2("Beard Styling",   "style"),
            new Level2("Head Shave",      "face")
        )),

        new Level1("Massage", "self_improvement", List.of(
            new Level2("Swedish Massage",      "self_improvement"),
            new Level2("Deep Tissue Massage",  "fitness_center"),
            new Level2("Hot Stone Massage",    "local_fire_department"),
            new Level2("Aromatherapy Massage", "air"),
            new Level2("Sports Massage",       "fitness_center")
        )),

        new Level1("Body Treatments", "bubble_chart", List.of(
            new Level2("Body Scrub", "bubble_chart"),
            new Level2("Body Wrap",  "wrap_text"),
            new Level2("Tanning",    "wb_sunny")
        ))
    );
}
