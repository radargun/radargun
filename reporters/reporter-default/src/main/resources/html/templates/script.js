function switch_visibility() {
	for (var i = 0; i < arguments.length; i++) {
		var element = document.getElementById(arguments[i]);
		if (element == null)
			return;

		if (element.classList.contains('collapse')) {
			element.classList.remove('collapse');
			element.classList.add('visible');
		} else {
			element.classList.remove('visible');
			element.classList.add('collapse');
		}
	}
}

function switch_visibility_by_class(class_name) {
	var elements = document.getElementsByClassName(class_name);
	for (var i = 0; i < elements.length; i++) {

		if (elements[i].classList.contains('collapse')) {
			elements[i].classList.remove('collapse');
			elements[i].classList.add('visible');
		} else {
			elements[i].classList.remove('visible');
			elements[i].classList.add('collapse');
		}
	}
}

function switch_li_display(id) {
	var element = document.getElementById(id);
	if (element == null)
		return;
	if (element.classList.contains('list-item')) {
		element.classList.remove('list-item');
		element.classList.add('none');
	} else {
		element.classList.remove('none');
		element.classList.add('list-item');
	}
}
function is_checked(id, checked) {
	var element = document.getElementById(id);
	return element == null ? false : element.checked
}
function reset_display(id, checked, display) {
	var element = document.getElementById(id);
	if (element == null)
		return;
	if (checked) {
		element.style.display = display;
	} else {
		element.style.display = 'none';
	}
}