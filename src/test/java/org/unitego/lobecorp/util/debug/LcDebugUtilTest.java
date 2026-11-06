package org.unitego.lobecorp.util.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.debug.LcDebugUtil.Category;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// 检查属性解析和运行期覆盖；不依赖 Minecraft 客户端或生产环境跳过。
public class LcDebugUtilTest {
	private static final String GLOBAL = "lobecorp.debug";
	private static final String SKILL = "lobecorp.debug.skill";
	private static final String ANIMATION = "lobecorp.debug.animation";
	private static final String ATTACHMENT = "lobecorp.debug.attachment";

	@AfterEach
	public void clearProperties() {
		System.clearProperty(GLOBAL);
		System.clearProperty(SKILL);
		System.clearProperty(ANIMATION);
		System.clearProperty(ATTACHMENT);
	}

	@Test
	public void missingPropertiesDisableEveryCategory() throws ReflectiveOperationException {
		assertEquals(Set.of(), parse());
	}

	@Test
	public void globalTrueEnablesAllAndExplicitFalseOverridesIt() throws ReflectiveOperationException {
		System.setProperty(GLOBAL, "true");
		System.setProperty(SKILL, "false");
		assertEquals(EnumSet.of(Category.ANIMATION, Category.ATTACHMENT), parse());
	}

	@Test
	public void individualTrueWorksWithoutGlobal() throws ReflectiveOperationException {
		System.setProperty(ANIMATION, "true");
		assertEquals(Set.of(Category.ANIMATION), parse());
	}

	@Test
	public void emptyOrNonTrueTextIsFalse() throws ReflectiveOperationException {
		System.setProperty(GLOBAL, "true");
		System.setProperty(SKILL, "");
		System.setProperty(ANIMATION, "yes");
		assertEquals(Set.of(Category.ATTACHMENT), parse());
	}

	@Test
	public void defaultEmptySetAllowsSingleCategoryEnable() {
		LcDebugUtil.setAllEnabled(false);
		LcDebugUtil.setEnabled(Category.SKILL, true);
		assertEquals(Set.of(Category.SKILL), LcDebugUtil.snapshot());
	}

	@Test
	public void allOffThenSingleCategoryEnableWorks() {
		LcDebugUtil.setAllEnabled(true);
		LcDebugUtil.setAllEnabled(false);
		LcDebugUtil.setEnabled(Category.ANIMATION, true);
		assertEquals(Set.of(Category.ANIMATION), LcDebugUtil.snapshot());
	}

	@Test
	public void setterChangesOnlyTheRequestedCategory() {
		LcDebugUtil.setAllEnabled(false);
		LcDebugUtil.setEnabled(Category.SKILL, true);
		assertEquals(Set.of(Category.SKILL), LcDebugUtil.snapshot());

		LcDebugUtil.setEnabled(Category.ANIMATION, true);
		assertEquals(EnumSet.of(Category.SKILL, Category.ANIMATION), LcDebugUtil.snapshot());

		LcDebugUtil.setEnabled(Category.SKILL, false);
		assertEquals(Set.of(Category.ANIMATION), LcDebugUtil.snapshot());
	}

	@Test
	public void setAllCoversEveryCategory() {
		LcDebugUtil.setAllEnabled(true);
		assertEquals(EnumSet.allOf(Category.class), LcDebugUtil.snapshot());

		LcDebugUtil.setAllEnabled(false);
		assertEquals(Set.of(), LcDebugUtil.snapshot());
	}

	private static Set<Category> parse() throws ReflectiveOperationException {
		Method method = LcDebugUtil.class.getDeclaredMethod("readProperties");
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		Set<Category> result = (Set<Category>) method.invoke(null);
		return result;
	}
}
