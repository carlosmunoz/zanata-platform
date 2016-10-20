package org.zanata.action.validator;


import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.jglue.cdiunit.AdditionalClasses;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.zanata.test.CdiUnitRunner;

import javax.inject.Inject;
import javax.validation.ConstraintValidatorContext;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CdiUnitRunner.class)
@AdditionalClasses(EmailValidator.class)
public class EmailListValidatorTest {

    @Inject
    private EmailListValidator validator;
    private ConstraintValidatorContext context = null;

    @Test
    public void nullIsValid() throws Exception {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    public void emptyIsValid() throws Exception {
        assertThat(validator.isValid("", context)).isTrue();
    }

    @Test
    public void aIsValid() throws Exception {
        assertThat(validator.isValid(" a@example.com ", context)).isTrue();
    }

    @Test
    public void aBIsValid() throws Exception {
        assertThat(validator.isValid("a@example.com, b@example.org", context))
                .isTrue();
    }

    @Test
    public void CIsInvalid() throws Exception {
        assertThat(validator.isValid("c", context)).isFalse();
    }

    @Test
    public void DIsValid() throws Exception {
        assertThat(validator.isValid("d@example", context)).isTrue();
    }

    @Test
    public void aBCDIsValid() throws Exception {
        assertThat(validator
                .isValid("a@example.com, b@example.org c d@example", context))
                .isFalse();
    }

    @Test
    public void nullOrEmptyStringIsValid() throws Exception {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
    }

    @Test
    public void isValidIfListsAreEmailsSeparatedByComma() {
        assertThat(validator.isValid("a@b.co,a@c.co", context)).isTrue();
        assertThat(validator.isValid("a@b.co ,a@c.co", context)).isTrue();
        assertThat(validator.isValid("a@b.co, a@c.co", context)).isTrue();
        assertThat(validator.isValid("a@b.co , a@c.co", context)).isTrue();
        assertThat(validator.isValid(" a@b.co , a@c.co", context)).isTrue();
        assertThat(validator.isValid(" a@b.co , a@c.co ", context)).isTrue();
    }

    @Test
    public void isInvalidIfListsContainNotValidEmail() {
        assertThat(validator.isValid("a@b.co,a b@1", context)).isFalse();
        assertThat(validator.isValid("a@b.co ,d", context)).isFalse();
    }
}
