package com.example.learning.module.validationbinding.service;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ValidationDataBindingExperimentService {

    public Map<String, Object> bindingVsValidationDemo() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("bindingFailureScenario", typeMismatchEvidence());
        evidence.put(
            "validationFailureAfterSuccessfulBinding",
            validationFailureEvidence()
        );
        return evidence;
    }

    private Map<String, Object> typeMismatchEvidence() {
        RegistrationTarget target = new RegistrationTarget();
        target.setAge(41);
        int ageBeforeBinding = target.getAge();
        DataBinder binder = new DataBinder(target, "registration");

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("age", "not-a-number");
        binder.bind(values);

        BindingResult result = binder.getBindingResult();
        FieldError error = result.getFieldError("age");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("inputAge", "not-a-number");
        evidence.put("targetAgeBeforeBinding", ageBeforeBinding);
        evidence.put("targetAgeAfterBinding", target.getAge());
        evidence.put("targetAgeUnchanged", target.getAge() == ageBeforeBinding);
        evidence.put("hasErrors", result.hasErrors());
        evidence.put("errorCode", error != null ? error.getCode() : null);
        evidence.put("rejectedValue", error != null ? error.getRejectedValue() : null);
        evidence.put("bindingFailure", error != null && error.isBindingFailure());
        return evidence;
    }

    private Map<String, Object> validationFailureEvidence() {
        RegistrationTarget target = new RegistrationTarget();
        DataBinder binder = new DataBinder(target, "registration");
        binder.addValidators(new AdultRegistrationValidator());

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("age", "15");
        binder.bind(values);

        BindingResult result = binder.getBindingResult();
        int errorsAfterBinding = result.getErrorCount();

        binder.validate();
        FieldError error = result.getFieldError("age");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("inputAge", "15");
        evidence.put("targetAgeAfterBinding", target.getAge());
        evidence.put("errorsAfterBinding", errorsAfterBinding);
        evidence.put("errorsAfterValidation", result.getErrorCount());
        evidence.put("errorCode", error != null ? error.getCode() : null);
        evidence.put("bindingFailure", error != null && error.isBindingFailure());
        return evidence;
    }

    public Map<String, Object> declarativeBindingDemo() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("requestedDisplayName", "Ada");
        evidence.put("requestedRole", "ADMIN");

        AccountTarget ordinaryTarget = newAccountTarget();
        DataBinder ordinaryBinder = new DataBinder(ordinaryTarget, "ordinaryAccount");
        ordinaryBinder.bind(accountValues());
        evidence.put(
            "ordinaryPropertyBinding",
            bindingState(ordinaryBinder, ordinaryTarget)
        );

        AccountTarget declarativeClosedTarget = newAccountTarget();
        DataBinder declarativeClosedBinder =
            new DataBinder(declarativeClosedTarget, "declarativeClosedAccount");
        declarativeClosedBinder.setDeclarativeBinding(true);
        declarativeClosedBinder.bind(accountValues());
        evidence.put(
            "declarativeWithoutAllowedFields",
            bindingState(declarativeClosedBinder, declarativeClosedTarget)
        );

        AccountTarget declarativeAllowedTarget = newAccountTarget();
        DataBinder declarativeAllowedBinder =
            new DataBinder(declarativeAllowedTarget, "declarativeAllowedAccount");
        declarativeAllowedBinder.setDeclarativeBinding(true);
        declarativeAllowedBinder.setAllowedFields("displayName");
        declarativeAllowedBinder.bind(accountValues());
        evidence.put(
            "declarativeWithDisplayNameAllowed",
            bindingState(declarativeAllowedBinder, declarativeAllowedTarget)
        );
        return evidence;
    }

    private MutablePropertyValues accountValues() {
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("displayName", "Ada");
        values.add("role", "ADMIN");
        return values;
    }

    private AccountTarget newAccountTarget() {
        AccountTarget target = new AccountTarget();
        target.setDisplayName("Before");
        target.setRole("USER");
        return target;
    }

    private Map<String, Object> bindingState(
        DataBinder binder,
        AccountTarget target
    ) {
        String[] allowedFields = binder.getAllowedFields();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("declarativeBinding", binder.isDeclarativeBinding());
        state.put("allowedFieldsConfigured", allowedFields != null);
        state.put(
            "allowedFields",
            allowedFields == null
                ? null
                : Arrays.asList(allowedFields)
        );
        state.put("targetDisplayName", target.getDisplayName());
        state.put("targetRole", target.getRole());
        state.put(
            "suppressedFields",
            Arrays.asList(binder.getBindingResult().getSuppressedFields())
        );
        state.put("hasErrors", binder.getBindingResult().hasErrors());
        return state;
    }

    private static final class AdultRegistrationValidator implements Validator {

        @Override
        public boolean supports(Class<?> clazz) {
            return RegistrationTarget.class.isAssignableFrom(clazz);
        }

        @Override
        public void validate(Object target, Errors errors) {
            RegistrationTarget registration = (RegistrationTarget) target;
            if (registration.getAge() < 18) {
                errors.rejectValue("age", "age.tooYoung", "Age must be at least 18");
            }
        }
    }

    public static final class RegistrationTarget {
        private int age;

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static final class AccountTarget {
        private String displayName;
        private String role;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
