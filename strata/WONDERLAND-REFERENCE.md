# Wonderland 1.6.28 resource inspection
Reference: user-provided com.samsung.android.wonderland.wallpaper_1.6.28 APK, inspected locally, binary resource table and XML decoded. No Samsung executable code or artwork redistributed.

- layer_list_item (res/3z.xml): 67 scalable-unit square cell; 60-unit image bottom-left; 27-unit change-image action top-right. Independent image/action views.
- settings_layer_list_item_ic_change (res/PO.xml): 27×27 vector, black circular background and white cycling arrows. STRATA uses its own drawn single-arrow glyph matching the user's newer screenshot.
- settings_layer_list_item_selected_outline (res/Zk.xml): red #F50000 stroke, two scalable units.
- settings_layer_list_item_thumbnail_background: #4A4A4A, 1dp internal padding.
- layer_setup_dialog (res/kN.xml), layer_setup_dialog_type (res/wA.xml): bottom sheet with title, list and action row; dark background #252525, title #FAFAFA, list #E5E5E5, blue #3E91FF.
- motion_effect_settings_layout (res/sB.xml), motion_effect_settings_sub_switch (res/j-.xml), motion_effect_settings_sub_transform (res/nn.xml): title, scrollable switch and transform groups.
- Original reference has X/Y/Z motion labels; STRATA retains independent X/Y controls per requested screenshot.

Use these structural measurements rather than a wholesale copy of Samsung's app or dependency on Samsung-specific UI classes.