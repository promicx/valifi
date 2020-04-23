package com.mlykotom.example_arch_viewmodel.single;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import com.mlykotom.valifi.ValiFi;
import com.mlykotom.valifi.ValiFiValidable;
import com.mlykotom.valifi.fields.ValiFieldCard;
import com.mlykotom.valifi.fields.ValiFieldUsername;
import com.mlykotom.valifi.fields.number.ValiFieldLong;
import com.mlykotom.valifi.fields.number.ValiFieldNumber;

public class SingleValidationViewModel extends ViewModel {
    public final ValiFieldUsername username = new ValiFieldUsername();
    public final ValiFieldUsername async = new ValiFieldUsername();
    public final ValiFieldLong numLong = new ValiFieldLong();
    public final ValiFieldCard creditCard = new ValiFieldCard();

    public final ValiFiValidable validable = username;

    public SingleValidationViewModel() {
        setupNumberValidator();
    }

    @Override
    protected void onCleared() {
        ValiFi.destroyFields(numLong, username, async, creditCard);
        super.onCleared();
    }

    /**
     * Example of initialization number validator.
     */
    private void setupNumberValidator() {
        final long requiredMinNumber = 13;
        numLong.addNumberValidator("This number must be greater than 13", new ValiFieldNumber.NumberValidator<Long>() {
            @Override
            public boolean isValid(@NonNull Long value) {
                return value > requiredMinNumber;
            }
        });
    }
}
