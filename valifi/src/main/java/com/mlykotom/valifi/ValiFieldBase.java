package com.mlykotom.valifi;

import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.BindingAdapter;
import androidx.databinding.Observable;
import androidx.databinding.library.baseAdapters.BR;

import com.google.android.material.textfield.TextInputLayout;
import com.mlykotom.valifi.exceptions.ValiFiValidatorException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for validation field. Holds value change listener and basic rules for validation.
 *
 * @param <ValueType> of the whole field (for now it's String and beta Calendar)
 */
@SuppressWarnings("unused")
public abstract class ValiFieldBase<ValueType> extends BaseObservable implements ValiFiValidable {

    private enum FieldType {
        TEXT, VALUE, ENABLE
    }

    @Nullable
    protected ValueType mValue;
    protected boolean mIsEmptyAllowed = false;
    @Nullable
    protected List<ValiFieldBase> mBoundFields;
    // --- maps of validators (to be validated)
    protected LinkedHashMap<PropertyValidator<ValueType>, String> mPropertyValidators = new LinkedHashMap<>();

    private IdentityHashMap<OnFieldChangeListener, FieldType> mOnFieldChanges = new IdentityHashMap<>();
    // --- delaying times
    protected long mErrorDelay;
    private boolean mIsChanged = false;
    @Nullable
    private String mError;
    @Nullable
    private String mLastError;
    volatile boolean mIsChecked = false;
    volatile boolean mLastIsError = true;
    volatile boolean mIsError = false;
    // --- others
    boolean mIsResetting = false;
    @Nullable
    private ValiFiForm mParentForm;

    protected OnPropertyChangedCallback mCallback = setupOnPropertyChangedCallback();

    private String mText;
    private boolean mEnable = true;

    @Bindable
    public boolean isEnable() {
        return mEnable;
    }

    public void setEnable(boolean enable) {
        if (enable == mEnable) return;
        this.mEnable = enable;
        notifyPropertyChanged(BR.enable);
        if (mOnFieldChanges.isEmpty()) return;
        for (Map.Entry<OnFieldChangeListener, FieldType> entry : mOnFieldChanges.entrySet()) {
            if (entry.getValue() == FieldType.ENABLE && entry.getKey() != null) {
                entry.getKey().onFieldChange();
            }
        }
    }

    @Nullable
    @Bindable
    public String getText() {
        return mText;
    }

    public void setText(@Nullable String text) {
        if ((Objects.equals(text, mText)) || (text != null && Objects.equals(text, mText))) return;
        mText = text;
        notifyPropertyChanged(BR.text);
        if (mOnFieldChanges.isEmpty()) return;
        for (Map.Entry<OnFieldChangeListener, FieldType> entry : mOnFieldChanges.entrySet()) {
            if (entry.getValue() == FieldType.TEXT && entry.getKey() != null) {
                entry.getKey().onFieldChange();
            }
        }
    }

    public interface PropertyValidator<T> {
        boolean isValid(@Nullable T value);
    }

    public ValiFieldBase() {
        this(null);
    }

    /**
     * @param defaultValue if not null, will mark that field is changed
     */
    public ValiFieldBase(@Nullable ValueType defaultValue) {
        this(defaultValue, true);
    }

    /**
     * @param defaultValue  is set to this field in construction
     * @param markAsChanged if default value marks this field as changed
     *                      (may be useful when loading data from network, not to trigger validation, because default value will be the same)
     */
    public ValiFieldBase(@Nullable ValueType defaultValue, boolean markAsChanged) {
        mErrorDelay = ValiFi.getErrorDelay();
        mValue = defaultValue;

        if (defaultValue != null && markAsChanged) {
            mIsChanged = true;
        }
        addOnPropertyChangedCallback(mCallback);
    }

    /**
     * Error binding for TextInputLayout
     *
     * @param view         TextInputLayout to be set with
     * @param errorMessage error message to show
     */
    @BindingAdapter("error")
    public static void setError(TextInputLayout view, String errorMessage) {
        view.setError(errorMessage);
    }

    /**
     * Error binding for EditText
     *
     * @param view         EditText to be set with
     * @param errorMessage error message to show
     */
    @BindingAdapter("error")
    public static void setError(EditText view, String errorMessage) {
        view.setError(errorMessage);
    }

    /**
     * Helper for destroying all specified fields
     *
     * @param fields to be destroyed
     * @see ValiFi#destroyFields(ValiFiValidable[])
     */
    public static void destroyAll(ValiFiValidable... fields) {
        ValiFi.destroyFields(fields);
    }

    /**
     * Checking for specific type if value is empty.
     * Used for checking if empty is allowed.
     *
     * @param actualValue value when checking
     * @return true when value is empty, false when values is not empty (e.g for String, use isEmpty())
     * @see #mCallback
     */
    protected abstract boolean whenThisFieldIsEmpty(@NonNull ValueType actualValue);

    /**
     * Any inherited field must be able to convert to String.
     * This is so that it's possible to show it in TextView/EditText
     *
     * @param value actual value to be converted
     * @return converted string (e.g. for Date = formatted string)
     */
    protected abstract String convertValueToString(@NonNull ValueType value);

    /**
     * Converts string to this value. This is called from data binding so if any class is convertable, override this
     *
     * @param value actual value from input
     * @return this value of type
     */
    @Nullable
    protected abstract ValueType convertStringToValue(@Nullable String value);

    /**
     * Might be used for checking submit buttons because isError might be true when data not changed
     * Field is valid when:
     * - no error is set
     * - validation is not in progress
     * - field was already changed OR was set that can be empty
     *
     * @return if property was changed, is not in progress, and is valid
     */
    @Bindable
    @Override
    public boolean isValid() {
        return mIsChecked && !mIsError & (mIsChanged | mIsEmptyAllowed);
    }

    @Bindable
    @Override
    public Boolean getHasError() {
        if (!mIsChecked) return false;
        return mIsError;
    }

    @Override
    public void resetError() {
        mIsResetting = true;
        mIsError = false;
        mError = null;
        mIsChanged = false;
        mIsChecked = false;

        notifyValidationChanged();
        refreshError();
        mIsResetting = false;
    }

    public void clearValidator() {
        mPropertyValidators.clear();
        mIsError = false;
        mError = null;
        notifyValidationChanged();
        refreshError();
    }

    /**
     * Removes property change callback and clears custom validators
     */
    public void destroy() {
        removeOnPropertyChangedCallback(mCallback);

        if (mPropertyValidators != null) {
            mPropertyValidators.clear();
            mPropertyValidators = null;
        }

        if (mBoundFields != null) {
            mBoundFields.clear();
            mBoundFields = null;
        }

        mParentForm = null;
        mIsChanged = false;
        mIsError = false;
        mIsEmptyAllowed = false;
        mIsChecked = false;
        removeAllOnFieldChange();
    }

    /**
     * Clears the state of the field (e.g. after submit of form).
     */
    @Override
    public void reset() {
        mIsResetting = true;
        mIsError = false;
        mError = null;
        mIsChanged = false;
        mIsChecked = false;
        setText(null);
        setValue(null);
        notifyValidationChanged();
        refreshError();
        mIsResetting = false;
    }

    /**
     * If you want to manually show error for the field
     */
    @Override
    public void validate() {
        mIsChecked = true;
        notifyValueChanged(true);
        refreshError();
    }

    /**
     * Allows empty field to be valid.
     * Useful when some field is not necessary but needs to be in proper format if filled.
     *
     * @param isEmptyAllowed if true, field may be empty or null to be valid
     * @return this, co validators can be chained
     */
    public ValiFieldBase<ValueType> setEmptyAllowed(boolean isEmptyAllowed) {
        mIsEmptyAllowed = isEmptyAllowed;
        return this;
    }

    /**
     * Sets how much it will take before error is shown.
     * Does not apply in cases when validation changes (e.g invalid to valid or vice versa)
     *
     * @param delayMillis positive number - time in milliseconds
     * @return this, validators can be chained
     * @see #setErrorDelay(ValiFiErrorDelay) for immediate or manual mode
     */
    public ValiFieldBase<ValueType> setErrorDelay(long delayMillis) {
        if (delayMillis <= 0) {
            throw new ValiFiValidatorException("Error delay must be positive");
        }

        mErrorDelay = delayMillis;
        return this;
    }

    /**
     * Sets whether validation will be immediate or never
     *
     * @param delayType either never or immediate
     * @return this, so validators can be chained
     * @see #setErrorDelay(long) for setting exact time
     */
    public ValiFieldBase<ValueType> setErrorDelay(ValiFiErrorDelay delayType) {
        mErrorDelay = delayType.delayMillis;
        return this;
    }

    /**
     * @return the containing value of the field
     */
    @Nullable
    public ValueType get() {
        return mValue;
    }

    /**
     * Wrapper for easy setting value
     *
     * @param value to be set and notified about change
     */
    public void set(@Nullable ValueType value) {
        if (Objects.equals(value, mValue) || (value != null && Objects.equals(value, mValue)))
            return;

        mValue = value;
        notifyValueChanged(false);
        for (Map.Entry<OnFieldChangeListener, FieldType> entry : mOnFieldChanges.entrySet()) {
            if (entry.getValue() == FieldType.VALUE && entry.getKey() != null) {
                entry.getKey().onFieldChange();
            }
        }
    }

    /**
     * This may be shown in layout as actual value
     *
     * @return value in string displayable in TextInputLayout/EditText
     */
    @Nullable
    @Bindable
    public String getValue() {
        if (mValue == null) return null;
        return convertValueToString(mValue);
    }

    /**
     * Sets new value (from binding)
     *
     * @param value to be set, if the same as older, skips
     */
    public void setValue(@Nullable String value) {
        set(convertStringToValue(value));
    }

    /**
     * Bundles this field to form
     *
     * @param form which validates all bundled fields
     */
    @Override
    public void setFormValidation(@Nullable ValiFiForm form) {
        mParentForm = form;
    }

    @Nullable
    public ValiFiForm getBoundForm() {
        return mParentForm;
    }

    @Nullable
    @Bindable
    public String getError() {
        return mError;
    }

    @Bindable
    public Boolean getIsError() {
        return mIsError && mIsChanged;
    }

    /**
     * May serve for specifying temporary error from different source.
     * This doesn't affect validation of the field, it just shows custom error message.
     * The error will be changed for the one from validation when input changes.
     *
     * @param error temporary error message
     */
    public void setError(@NonNull String error) {
        mError = error;
        refreshError();
    }

    /**
     * @param errorResource to be shown (got from app's context)
     * @param targetField   validates with this field
     * @return this, so validators can be chained
     * @see #addVerifyFieldValidator(String, ValiFieldBase)
     */
    public ValiFieldBase<ValueType> addVerifyFieldValidator(@StringRes int errorResource, final ValiFieldBase<ValueType> targetField) {
        String errorMessage = getString(errorResource);
        return addVerifyFieldValidator(errorMessage, targetField);
    }

    /**
     * Validates equality of this value and specified field's value.
     * If specified field changes, it notifies this field's change listener.
     *
     * @param errorMessage to be shown if not valid
     * @param targetField  validates with this field
     * @return this, so validators can be chained
     */
    public ValiFieldBase<ValueType> addVerifyFieldValidator(String errorMessage, final ValiFieldBase<ValueType> targetField) {
        addCustomValidator(errorMessage, new PropertyValidator<ValueType>() {
            @Override
            public boolean isValid(@Nullable ValueType value) {
                ValueType fieldVal = targetField.get();
                return (value == targetField.get()) || (value != null && value.equals(fieldVal));
            }
        });

        targetField.addBoundField(this);
        return this;
    }

    /**
     * Adds validator without error message.
     * This means no error will be shown, but field won't be valid
     *
     * @param validator implementation of validation
     * @return this, so validators can be chained
     * @see #addCustomValidator(String, PropertyValidator)
     */
    public ValiFieldBase<ValueType> addCustomValidator(PropertyValidator<ValueType> validator) {
        return addCustomValidator(null, validator);
    }

    /**
     * Adds custom validator with error message string resource
     *
     * @param errorResource string resource shown when validator not valid
     * @param validator     implementation of validation
     * @return this, so validators can be chained
     * @see #addCustomValidator(String, PropertyValidator)
     */
    public ValiFieldBase<ValueType> addCustomValidator(@StringRes int errorResource, PropertyValidator<ValueType> validator) {
        String errorMessage = getString(errorResource);
        return addCustomValidator(errorMessage, validator);
    }

    /**
     * Adds custom validator which will be validated when value property changes.
     *
     * @param errorMessage to be shown if field does not meet this validation
     * @param validator    implementation of validation
     * @return this, so validators can be chained
     */
    public ValiFieldBase<ValueType> addCustomValidator(String errorMessage, PropertyValidator<ValueType> validator) {
        if (mPropertyValidators == null) {
            mPropertyValidators = new LinkedHashMap<>();
        }

        mPropertyValidators.put(validator, errorMessage);
        if (mIsChanged) {
            notifyValueChanged(true);
        }
        return this;
    }

    /**
     * Removes property validator
     *
     * @param validator which was set before
     * @return true, if successfully removed, false otherwise
     */
    public boolean removeValidator(@NonNull PropertyValidator<ValueType> validator) {
        return mPropertyValidators.remove(validator) != null;
    }

    /**
     * If you want to manually show error for the field
     */
    @Override
    public void refreshError() {
        notifyPropertyChanged(BR.error);
        notifyPropertyChanged(BR.hasError);
    }

    /**
     * Internaly fields can be binded together so that when one changes, it notifies others
     *
     * @param field to be notified when this field changed
     */
    protected void addBoundField(ValiFieldBase field) {
        if (mBoundFields == null) {
            mBoundFields = new ArrayList<>();
        }
        mBoundFields.add(field);
    }

    protected int getErrorRes(@ValiFi.Builder.ValiFiErrorResource int field) {
        return ValiFi.getErrorRes(field);
    }

    protected PropertyValidator<String> getValidator(@ValiFi.Builder.ValiFiPattern int field) {
        return ValiFi.getValidator(field);
    }

    /**
     * Serves for getting strings in fields
     *
     * @param stringRes  R.string.*
     * @param formatArgs the same as in context.getString()
     * @return formatted String | in case of tests, returns "string-*"
     */
    protected String getString(@StringRes int stringRes, Object... formatArgs) {
        return ValiFi.getString(stringRes, formatArgs);
    }

    /**
     * Sets error state to this field + optionally to binded form
     *
     * @param isError      whether there's error or no
     * @param errorMessage to be shown
     */
    protected void setIsError(boolean isError, @Nullable String errorMessage) {
        mIsChanged = true;
        mIsError = isError;
        mError = errorMessage;
        notifyValidationChanged();
    }

    /**
     * Notifies that value changed (internally)
     *
     * @param isImmediate if true, does not call binding notifier
     */
    protected void notifyValueChanged(boolean isImmediate) {
        if (isImmediate) {
            mCallback.onPropertyChanged(null, BR.value);
        } else {
            notifyPropertyChanged(BR.value);
        }
    }

    /**
     * Notifies that field's validation flag changed
     */
    protected void notifyValidationChanged() {
        notifyPropertyChanged(BR.valid);
        refreshError();
        if (mParentForm != null) {
            mParentForm.notifyValidationChanged(this);
        }
    }

    /**
     * Notifies bound fields about value change
     */
    private void notifyBoundFieldsValueChanged() {
        if (mBoundFields == null) return;

        for (ValiFieldBase field : mBoundFields) {
            if (!field.mIsChanged) continue;    // notifies only changed items
            field.notifyValueChanged(true);
        }
    }

    /**
     * Checks synchronous validators one by one and sets error to the field if any of them is invalid
     */
    private void checkBlockingValidators() {
        if (mPropertyValidators == null || mPropertyValidators.isEmpty()) return;
        for (Map.Entry<PropertyValidator<ValueType>, String> entry : mPropertyValidators.entrySet()) {
            if (!entry.getKey().isValid(mValue)) {
                setIsError(true, entry.getValue());
                return;
            }
        }

        // set valid
        setIsError(false, null);
    }

    private OnPropertyChangedCallback setupOnPropertyChangedCallback() {
        return new OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int brId) {
                if (brId != BR.value || mIsResetting) return;

                // ad 1) notifying bound fields
                notifyBoundFieldsValueChanged();

                // ad 2) checking if value can be empty
                if (mIsEmptyAllowed && (mValue == null || whenThisFieldIsEmpty(mValue))) {
                    setIsError(false, null);
                    return;
                }

                // ad 3) validating synchronous validators
                checkBlockingValidators();
            }
        };
    }

    public void addOnFieldTextChange(OnFieldChangeListener l) {
        if (mOnFieldChanges.containsKey(l)) return;
        mOnFieldChanges.put(l, FieldType.TEXT);
    }

    public void addOnFieldValueChange(OnFieldChangeListener l) {
        if (mOnFieldChanges.containsKey(l)) return;
        mOnFieldChanges.put(l, FieldType.VALUE);
    }

    public void addOnFieldEnableChange(OnFieldChangeListener l) {
        if (mOnFieldChanges.containsKey(l)) return;
        mOnFieldChanges.put(l, FieldType.ENABLE);
    }

    public void removeOnFieldChange(OnFieldChangeListener l) {
        if (mOnFieldChanges.isEmpty()) return;
        mOnFieldChanges.remove(l);
    }

    public void removeAllOnFieldChange() {
        if (mOnFieldChanges.isEmpty()) return;
        mOnFieldChanges.clear();
    }
}