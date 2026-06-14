package com.fifers.leathercalculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "leather_calculator_prefs";
    private static final String KEY_PRODUCTS = "products";
    private static final String KEY_CALCULATIONS = "calculation_records";
    private static final String[] MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    private final ArrayList<Product> products = new ArrayList<>();
    private ProductListAdapter listAdapter;
    private ArrayAdapter<String> productSearchAdapter;

    private EditText productNameEditText;
    private EditText defaultUsageEditText;
    private EditText quantityEditText;
    private EditText actualLeatherEditText;
    private TextView resultTextView;
    private TextView resultStatusTextView;
    private TextView resultDifferenceTextView;
    private TextView resultProductTextView;
    private TextView resultExpectedTextView;
    private TextView resultActualTextView;
    private TextView dashboardProductCountTextView;
    private TextView dashboardRecordCountTextView;
    private TextView catalogCountTextView;
    private TextView emptyProductsTextView;
    private View resultCard;
    private AutoCompleteTextView productSearchEditText;
    private Spinner monthSpinner;
    private ListView productsListView;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyBottomSystemInset(R.id.mainScreenRoot);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        bindViews();
        loadProducts();
        setupAdapters();
        setupActions();
        updateDashboardStats();
        updateProductEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences != null) {
            updateDashboardStats();
        }
    }

    private void bindViews() {
        productNameEditText = findViewById(R.id.productNameEditText);
        defaultUsageEditText = findViewById(R.id.defaultUsageEditText);
        quantityEditText = findViewById(R.id.quantityEditText);
        actualLeatherEditText = findViewById(R.id.actualLeatherEditText);
        resultCard = findViewById(R.id.resultCard);
        resultTextView = findViewById(R.id.resultTextView);
        resultStatusTextView = findViewById(R.id.resultStatusTextView);
        resultDifferenceTextView = findViewById(R.id.resultDifferenceTextView);
        resultProductTextView = findViewById(R.id.resultProductTextView);
        resultExpectedTextView = findViewById(R.id.resultExpectedTextView);
        resultActualTextView = findViewById(R.id.resultActualTextView);
        dashboardProductCountTextView = findViewById(R.id.dashboardProductCountTextView);
        dashboardRecordCountTextView = findViewById(R.id.dashboardRecordCountTextView);
        catalogCountTextView = findViewById(R.id.catalogCountTextView);
        emptyProductsTextView = findViewById(R.id.emptyProductsTextView);
        productSearchEditText = findViewById(R.id.productSearchEditText);
        monthSpinner = findViewById(R.id.monthSpinner);
        productsListView = findViewById(R.id.productsListView);
        Button addProductButton = findViewById(R.id.addProductButton);
        Button calculateButton = findViewById(R.id.calculateButton);
        Button recordsButton = findViewById(R.id.recordsButton);

        addProductButton.setOnClickListener(v -> addProduct());
        calculateButton.setOnClickListener(v -> calculateDifference());
        recordsButton.setOnClickListener(v -> startActivity(new Intent(this, MonthlyResultsActivity.class)));
    }

    private void setupAdapters() {
        listAdapter = new ProductListAdapter();
        productsListView.setAdapter(listAdapter);

        productSearchAdapter = new ArrayAdapter<>(this, R.layout.autocomplete_dropdown_item,
                R.id.dropdownText, productNames());
        productSearchEditText.setAdapter(productSearchAdapter);
        productSearchEditText.setThreshold(1);
        productSearchEditText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && !products.isEmpty()) {
                productSearchEditText.showDropDown();
            }
        });
        productSearchEditText.setOnClickListener(view -> {
            if (!products.isEmpty()) {
                productSearchEditText.showDropDown();
            }
        });

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, R.layout.spinner_selected_item,
                R.id.spinnerText, MONTHS);
        monthAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);
    }

    private void setupActions() {
        productsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Product product = products.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("حذف محصول")
                    .setMessage("محصول «" + product.name + "» حذف شود؟")
                    .setPositiveButton("حذف", (dialog, which) -> {
                        products.remove(position);
                        saveProducts();
                        refreshAdapters();
                        Toast.makeText(this, "محصول حذف شد.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("لغو", null)
                    .show();
            return true;
        });
    }

    private void addProduct() {
        String name = productNameEditText.getText().toString().trim();
        double defaultUsage = parseNumber(defaultUsageEditText.getText().toString());

        if (name.isEmpty()) {
            showToast("نام محصول را وارد کنید.");
            return;
        }
        if (findProductByName(name) != null) {
            showToast("محصولی با این نام قبلاً ثبت شده است.");
            return;
        }
        if (Double.isNaN(defaultUsage) || defaultUsage <= 0) {
            showToast("مصرف استاندارد باید عددی بزرگ‌تر از صفر باشد.");
            return;
        }

        products.add(new Product(name, defaultUsage));
        saveProducts();
        refreshAdapters();

        productNameEditText.setText("");
        defaultUsageEditText.setText("");
        productSearchEditText.setText(name, false);
        showToast("محصول با موفقیت ثبت شد.");
    }

    private void calculateDifference() {
        if (products.isEmpty()) {
            showToast("ابتدا حداقل یک محصول اضافه کنید.");
            return;
        }

        String searchedProductName = productSearchEditText.getText().toString().trim();
        Product product = findProductByName(searchedProductName);
        if (product == null) {
            showToast("یک محصول معتبر از نتایج جستجو انتخاب کنید.");
            return;
        }

        String month = String.valueOf(monthSpinner.getSelectedItem());
        double quantity = parseNumber(quantityEditText.getText().toString());
        double actualLeather = parseNumber(actualLeatherEditText.getText().toString());

        if (Double.isNaN(quantity) || quantity <= 0) {
            showToast("تعداد محصول را درست وارد کنید.");
            return;
        }
        if (Double.isNaN(actualLeather) || actualLeather < 0) {
            showToast("چرم مصرفی واقعی را درست وارد کنید.");
            return;
        }

        double expectedLeather = quantity * product.defaultUsage;
        double difference = actualLeather - expectedLeather;
        String status = cuttingStatus(difference);

        showCalculationResult(month, product, quantity, actualLeather, expectedLeather, difference, status);
        saveCalculation(month, product, quantity, actualLeather, expectedLeather, difference, status);
        showToast("نتیجه در سوابق «" + month + "» ذخیره شد.");
    }

    private void showCalculationResult(String month, Product product, double quantity, double actualLeather,
                                       double expectedLeather, double difference, String status) {
        resultCard.setVisibility(View.VISIBLE);
        resultStatusTextView.setText(status);
        resultDifferenceTextView.setText(formatSignedNumber(difference) + " پا");
        resultProductTextView.setText("ماه «" + month + "»  •  " + product.name);
        resultExpectedTextView.setText(formatNumber(expectedLeather) + " پا");
        resultActualTextView.setText(formatNumber(actualLeather) + " پا");
        applyStatusStyle(resultStatusTextView, resultDifferenceTextView, difference);

        String details = "مصرف هر عدد: " + formatNumber(product.defaultUsage) + " پا"
                + "   •   تعداد تولید: " + formatNumber(quantity)
                + "\nنتیجه ثبت شد و در گزارش ماهانه قابل پیگیری است.";
        resultTextView.setText(details);
    }

    private void applyStatusStyle(TextView statusView, TextView differenceView, double difference) {
        if (difference < 0) {
            resultCard.setBackgroundResource(R.drawable.bg_result_card_good);
            statusView.setBackgroundResource(R.drawable.bg_badge_good);
            statusView.setTextColor(getColor(R.color.success_dark));
            differenceView.setTextColor(getColor(R.color.success_dark));
        } else if (difference > 0) {
            resultCard.setBackgroundResource(R.drawable.bg_result_card_bad);
            statusView.setBackgroundResource(R.drawable.bg_badge_bad);
            statusView.setTextColor(getColor(R.color.danger_dark));
            differenceView.setTextColor(getColor(R.color.danger_dark));
        } else {
            resultCard.setBackgroundResource(R.drawable.bg_result_card_neutral);
            statusView.setBackgroundResource(R.drawable.bg_badge_neutral);
            statusView.setTextColor(getColor(R.color.neutral_dark));
            differenceView.setTextColor(getColor(R.color.text_primary));
        }
    }

    private String cuttingStatus(double difference) {
        if (difference > 0) {
            return "برش بد";
        } else if (difference < 0) {
            return "برش خوب";
        }
        return "برش بدون اختلاف";
    }

    private Product findProductByName(String name) {
        for (Product product : products) {
            if (product.name.equalsIgnoreCase(name.trim())) {
                return product;
            }
        }
        return null;
    }

    private void saveCalculation(String month, Product product, double quantity, double actualLeather,
                                 double expectedLeather, double difference, String status) {
        JSONArray records = readJsonArray(KEY_CALCULATIONS);
        JSONObject object = new JSONObject();
        try {
            object.put("month", month);
            object.put("productName", product.name);
            object.put("defaultUsage", product.defaultUsage);
            object.put("quantity", quantity);
            object.put("actualLeather", actualLeather);
            object.put("expectedLeather", expectedLeather);
            object.put("difference", difference);
            object.put("status", status);
            object.put("createdAt", System.currentTimeMillis());
            records.put(object);
            preferences.edit().putString(KEY_CALCULATIONS, records.toString()).apply();
            updateDashboardStats();
        } catch (JSONException ignored) {
            showToast("ذخیره سابقه انجام نشد.");
        }
    }

    private JSONArray readJsonArray(String key) {
        String json = preferences.getString(key, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void loadProducts() {
        products.clear();
        String savedJson = preferences.getString(KEY_PRODUCTS, null);

        if (savedJson == null) {
            products.add(new Product("دلار", 4));
            saveProducts();
            return;
        }

        try {
            JSONArray array = new JSONArray(savedJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String name = object.optString("name", "").trim();
                double defaultUsage = object.optDouble("defaultUsage", 0);
                if (!name.isEmpty() && defaultUsage > 0) {
                    products.add(new Product(name, defaultUsage));
                }
            }
        } catch (JSONException e) {
            products.clear();
            products.add(new Product("دلار", 4));
            saveProducts();
        }
    }

    private void saveProducts() {
        JSONArray array = new JSONArray();
        for (Product product : products) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", product.name);
                object.put("defaultUsage", product.defaultUsage);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY_PRODUCTS, array.toString()).apply();
    }

    private void refreshAdapters() {
        listAdapter.notifyDataSetChanged();
        productSearchAdapter.clear();
        productSearchAdapter.addAll(productNames());
        productSearchAdapter.notifyDataSetChanged();
        updateDashboardStats();
        updateProductEmptyState();
    }

    private void updateDashboardStats() {
        if (dashboardProductCountTextView == null || dashboardRecordCountTextView == null || catalogCountTextView == null) {
            return;
        }
        int recordCount = readJsonArray(KEY_CALCULATIONS).length();
        dashboardProductCountTextView.setText(toPersianDigits(String.valueOf(products.size())));
        dashboardRecordCountTextView.setText(toPersianDigits(String.valueOf(recordCount)));
        catalogCountTextView.setText(toPersianDigits(String.valueOf(products.size())) + " محصول");
    }

    private void updateProductEmptyState() {
        if (emptyProductsTextView == null || productsListView == null) {
            return;
        }
        boolean isEmpty = products.isEmpty();
        emptyProductsTextView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        productsListView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private List<String> productNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Product product : products) {
            names.add(product.name);
        }
        return names;
    }

    private double parseNumber(String value) {
        if (value == null) return Double.NaN;
        String normalized = toEnglishDigits(value.trim())
                .replace('٫', '.')
                .replace('،', '.')
                .replace(',', '.');
        normalized = normalized.replaceAll("[^0-9.\\-]", "");
        if (normalized.isEmpty() || normalized.equals(".") || normalized.equals("-")) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private String formatNumber(double value) {
        DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
        decimalFormat.applyPattern("#,##0.##");
        return toPersianDigits(decimalFormat.format(value));
    }

    private String formatSignedNumber(double value) {
        String sign = value > 0 ? "+" : "";
        return sign + formatNumber(value);
    }

    private String toEnglishDigits(String value) {
        return value
                .replace('۰', '0')
                .replace('۱', '1')
                .replace('۲', '2')
                .replace('۳', '3')
                .replace('۴', '4')
                .replace('۵', '5')
                .replace('۶', '6')
                .replace('۷', '7')
                .replace('۸', '8')
                .replace('۹', '9')
                .replace('٠', '0')
                .replace('١', '1')
                .replace('٢', '2')
                .replace('٣', '3')
                .replace('٤', '4')
                .replace('٥', '5')
                .replace('٦', '6')
                .replace('٧', '7')
                .replace('٨', '8')
                .replace('٩', '9');
    }

    private String toPersianDigits(String value) {
        return value
                .replace('0', '۰')
                .replace('1', '۱')
                .replace('2', '۲')
                .replace('3', '۳')
                .replace('4', '۴')
                .replace('5', '۵')
                .replace('6', '۶')
                .replace('7', '۷')
                .replace('8', '۸')
                .replace('9', '۹');
    }

    private void applyBottomSystemInset(int viewId) {
        View rootView = findViewById(viewId);
        if (rootView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        final int initialLeft = rootView.getPaddingLeft();
        final int initialTop = rootView.getPaddingTop();
        final int initialRight = rootView.getPaddingRight();
        final int initialBottom = rootView.getPaddingBottom();
        rootView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(initialLeft, initialTop, initialRight,
                    initialBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        rootView.requestApplyInsets();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class ProductListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return products.size();
        }

        @Override
        public Product getItem(int position) {
            return products.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_product, parent, false);
            }
            Product product = getItem(position);
            TextView nameView = view.findViewById(R.id.productRowNameTextView);
            TextView usageView = view.findViewById(R.id.productRowUsageTextView);
            nameView.setText(product.name);
            usageView.setText("مصرف استاندارد: " + formatNumber(product.defaultUsage) + " پا برای هر عدد");
            return view;
        }
    }

    private static class Product {
        final String name;
        final double defaultUsage;

        Product(String name, double defaultUsage) {
            this.name = name;
            this.defaultUsage = defaultUsage;
        }
    }
}
